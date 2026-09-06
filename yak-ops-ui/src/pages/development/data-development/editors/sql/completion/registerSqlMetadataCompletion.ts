import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import type { DevelopmentId } from '../../../types';
import { loadSqlColumns, loadSqlTables } from '../metadata/sqlMetadataCache';
import {
  getSqlMetadataContext,
  type SqlMetadataContext,
} from '../metadata/sqlMetadataContextStore';
import type {
  SqlCatalogColumn,
  SqlCatalogTable,
} from '../metadata/sqlMetadataService';

type SqlLexicalState =
  | 'code'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'bracket-identifier'
  | 'line-comment'
  | 'block-comment';

interface SqlTableReference {
  database?: string;
  schema?: string;
  table: string;
  alias?: string;
}

let providerDisposable: monaco.IDisposable | undefined;
let providerConsumers = 0;
const modelNodeIds = new Map<string, DevelopmentId>();

const STOP_ALIAS_WORDS = new Set([
  'WHERE',
  'JOIN',
  'LEFT',
  'RIGHT',
  'FULL',
  'INNER',
  'OUTER',
  'CROSS',
  'ON',
  'GROUP',
  'ORDER',
  'HAVING',
  'LIMIT',
  'OFFSET',
  'UNION',
  'EXCEPT',
  'INTERSECT',
  'SET',
  'VALUES',
  'RETURNING',
]);

const identifierPart = '(?:`[^`]+`|"[^"]+"|\\[[^\\]]+\\]|[A-Za-z_$][\\w$]*)';
const qualifiedIdentifier = `${identifierPart}(?:\\s*\\.\\s*${identifierPart}){0,2}`;
const tableReferencePattern = new RegExp(
  `\\b(?:FROM|JOIN)\\s+(${qualifiedIdentifier})(?:\\s+(?:AS\\s+)?([A-Za-z_$][\\w$]*))?`,
  'gi',
);

const getTextBeforePosition = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) =>
  model.getValueInRange(
    new monaco.Range(1, 1, position.lineNumber, position.column),
  );

const getCurrentStatementText = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const sql = model.getValue();
  const offset = model.getOffsetAt(position);
  const start = sql.lastIndexOf(';', Math.max(0, offset - 1)) + 1;
  const nextSeparator = sql.indexOf(';', offset);
  const end = nextSeparator < 0 ? sql.length : nextSeparator;
  return sql.slice(start, end);
};

const getLexicalState = (text: string): SqlLexicalState => {
  let state: SqlLexicalState = 'code';

  for (let index = 0; index < text.length; index += 1) {
    const current = text[index];
    const next = text[index + 1];

    if (state === 'line-comment') {
      if (current === '\n') state = 'code';
      continue;
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        state = 'code';
        index += 1;
      }
      continue;
    }
    if (state === 'single-quote') {
      if (current === "'" && next === "'") {
        index += 1;
        continue;
      }
      if (current === "'") state = 'code';
      continue;
    }
    if (state === 'double-quote') {
      if (current === '"' && next === '"') {
        index += 1;
        continue;
      }
      if (current === '"') state = 'code';
      continue;
    }
    if (state === 'backtick') {
      if (current === '`' && next === '`') {
        index += 1;
        continue;
      }
      if (current === '`') state = 'code';
      continue;
    }
    if (state === 'bracket-identifier') {
      if (current === ']' && next === ']') {
        index += 1;
        continue;
      }
      if (current === ']') state = 'code';
      continue;
    }

    if (current === '-' && next === '-') {
      state = 'line-comment';
      index += 1;
    } else if (current === '/' && next === '*') {
      state = 'block-comment';
      index += 1;
    } else if (current === "'") {
      state = 'single-quote';
    } else if (current === '"') {
      state = 'double-quote';
    } else if (current === '`') {
      state = 'backtick';
    } else if (current === '[') {
      state = 'bracket-identifier';
    }
  }

  return state;
};

const stripQuotedValue = (value: string) => {
  const normalized = value.trim();
  if (
    (normalized.startsWith('`') && normalized.endsWith('`')) ||
    (normalized.startsWith('"') && normalized.endsWith('"')) ||
    (normalized.startsWith('[') && normalized.endsWith(']'))
  ) {
    return normalized.slice(1, -1);
  }
  return normalized;
};

const splitQualifiedIdentifier = (value: string) => {
  const parts: string[] = [];
  let current = '';
  let quote: '`' | '"' | '[' | undefined;

  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (!quote && (char === '`' || char === '"' || char === '[')) {
      quote = char as '`' | '"' | '[';
      current += char;
      continue;
    }
    if (quote === '`' && char === '`') quote = undefined;
    else if (quote === '"' && char === '"') quote = undefined;
    else if (quote === '[' && char === ']') quote = undefined;

    if (!quote && char === '.') {
      if (current.trim()) parts.push(stripQuotedValue(current));
      current = '';
      continue;
    }
    current += char;
  }

  if (current.trim()) parts.push(stripQuotedValue(current));
  return parts;
};

const tableReferenceFromParts = (
  parts: string[],
  alias: string | undefined,
  context: SqlMetadataContext,
): SqlTableReference | undefined => {
  if (!parts.length) return undefined;
  const table = parts[parts.length - 1];
  if (!table) return undefined;

  if (parts.length >= 3) {
    return {
      database: parts[parts.length - 3],
      schema: parts[parts.length - 2],
      table,
      alias,
    };
  }

  if (parts.length === 2) {
    const first = parts[0];
    const mysqlLike = ['MYSQL', 'MARIADB', 'DORIS'].includes(
      (context.dbType || '').toUpperCase(),
    );
    return mysqlLike
      ? { database: first, table, alias }
      : { database: context.database, schema: first, table, alias };
  }

  return {
    database: context.database,
    schema: context.schema,
    table,
    alias,
  };
};

const parseTableReferences = (
  sql: string,
  context: SqlMetadataContext,
): SqlTableReference[] => {
  const references: SqlTableReference[] = [];
  tableReferencePattern.lastIndex = 0;
  let match = tableReferencePattern.exec(sql);
  while (match) {
    const rawAlias = match[2];
    const alias =
      rawAlias && !STOP_ALIAS_WORDS.has(rawAlias.toUpperCase())
        ? rawAlias
        : undefined;
    const reference = tableReferenceFromParts(
      splitQualifiedIdentifier(match[1]),
      alias,
      context,
    );
    if (reference) references.push(reference);
    match = tableReferencePattern.exec(sql);
  }
  return references;
};

const getCompletionRange = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const word = model.getWordUntilPosition(position);
  return new monaco.Range(
    position.lineNumber,
    word.startColumn,
    position.lineNumber,
    word.endColumn,
  );
};

const getQualifier = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const line = model.getLineContent(position.lineNumber).slice(0, position.column - 1);
  const match = line.match(/([A-Za-z_$][\w$]*)\s*\.\s*[A-Za-z_$\d]*$/);
  return match?.[1];
};

const isTablePosition = (textBeforePosition: string) =>
  /\b(?:FROM|JOIN|UPDATE|INTO)\s+(?:[A-Za-z_$][\w$]*\s*\.\s*){0,2}[A-Za-z_$\d]*$/i.test(
    textBeforePosition,
  );

const columnSuggestion = (
  column: SqlCatalogColumn,
  range: monaco.Range,
  owner: string,
): monaco.languages.CompletionItem => ({
  label: column.name,
  kind: monaco.languages.CompletionItemKind.Field,
  insertText: column.name,
  detail: [column.typeName, column.primaryKey ? 'PK' : undefined, owner]
    .filter(Boolean)
    .join(' · '),
  documentation: column.remarks || undefined,
  sortText: `1-${String(column.ordinalPosition ?? 9999).padStart(5, '0')}-${column.name}`,
  range,
});

const tableSuggestion = (
  table: SqlCatalogTable,
  range: monaco.Range,
): monaco.languages.CompletionItem => ({
  label: table.name,
  kind:
    table.type?.toUpperCase() === 'VIEW'
      ? monaco.languages.CompletionItemKind.Interface
      : monaco.languages.CompletionItemKind.Struct,
  insertText: table.name,
  detail: [table.type || 'TABLE', table.database, table.schema]
    .filter(Boolean)
    .join(' · '),
  documentation: table.remarks || undefined,
  sortText: `1-${table.name}`,
  range,
});

const findReference = (
  qualifier: string,
  references: SqlTableReference[],
) => {
  const normalized = qualifier.toLowerCase();
  return references.find(
    (reference) =>
      reference.alias?.toLowerCase() === normalized ||
      reference.table.toLowerCase() === normalized,
  );
};

const provideMetadataSuggestions = async (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  token: monaco.CancellationToken,
): Promise<monaco.languages.CompletionList> => {
  const nodeId = modelNodeIds.get(model.uri.toString());
  const context = nodeId ? getSqlMetadataContext(nodeId) : undefined;
  if (!context?.dataSourceId) return { suggestions: [] };

  const textBeforePosition = getTextBeforePosition(model, position);
  if (getLexicalState(textBeforePosition) !== 'code') {
    return { suggestions: [] };
  }

  const range = getCompletionRange(model, position);
  const qualifier = getQualifier(model, position);
  const references = parseTableReferences(
    getCurrentStatementText(model, position),
    context,
  );

  try {
    if (qualifier) {
      const reference = findReference(qualifier, references);
      if (reference) {
        const columns = await loadSqlColumns(context, reference.table, {
          database: reference.database,
          schema: reference.schema,
        });
        if (token.isCancellationRequested) return { suggestions: [] };
        return {
          suggestions: columns.map((column) =>
            columnSuggestion(column, range, reference.alias || reference.table),
          ),
        };
      }

      if (
        qualifier.toLowerCase() === context.schema?.toLowerCase() ||
        qualifier.toLowerCase() === context.database?.toLowerCase()
      ) {
        const tables = await loadSqlTables(context);
        if (token.isCancellationRequested) return { suggestions: [] };
        return { suggestions: tables.map((table) => tableSuggestion(table, range)) };
      }

      return { suggestions: [] };
    }

    if (isTablePosition(textBeforePosition)) {
      const tables = await loadSqlTables(context);
      if (token.isCancellationRequested) return { suggestions: [] };
      return { suggestions: tables.map((table) => tableSuggestion(table, range)) };
    }

    if (references.length === 1) {
      const reference = references[0];
      const columns = await loadSqlColumns(context, reference.table, {
        database: reference.database,
        schema: reference.schema,
      });
      if (token.isCancellationRequested) return { suggestions: [] };
      return {
        suggestions: columns.map((column) =>
          columnSuggestion(column, range, reference.table),
        ),
      };
    }
  } catch {
    // Metadata completion is best-effort. Keep SQL editing/builtin completion available.
  }

  return { suggestions: [] };
};

const createProvider = () =>
  monaco.languages.registerCompletionItemProvider('sql', {
    triggerCharacters: ['.'],
    provideCompletionItems: provideMetadataSuggestions,
  });

export const bindSqlMetadataModel = (
  modelUri: string,
  nodeId: DevelopmentId,
): monaco.IDisposable => {
  modelNodeIds.set(modelUri, nodeId);
  return {
    dispose: () => {
      if (modelNodeIds.get(modelUri) === nodeId) modelNodeIds.delete(modelUri);
    },
  };
};

export const acquireSqlMetadataCompletionProvider = (): monaco.IDisposable => {
  providerConsumers += 1;
  if (!providerDisposable) providerDisposable = createProvider();

  let released = false;
  return {
    dispose: () => {
      if (released) return;
      released = true;
      providerConsumers = Math.max(0, providerConsumers - 1);
      if (providerConsumers > 0) return;
      providerDisposable?.dispose();
      providerDisposable = undefined;
    },
  };
};
