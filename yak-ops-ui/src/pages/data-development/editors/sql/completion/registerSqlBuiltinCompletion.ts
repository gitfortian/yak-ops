import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import {
  SQL_BUILTIN_FUNCTIONS,
  SQL_KEYWORDS,
} from './sqlBuiltinCatalog';

type SqlLexicalState =
  | 'code'
  | 'single-quote'
  | 'double-quote'
  | 'backtick'
  | 'bracket-identifier'
  | 'line-comment'
  | 'block-comment';

let providerDisposable: monaco.IDisposable | undefined;
let providerConsumers = 0;

const getTextBeforePosition = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) =>
  model.getValueInRange(
    new monaco.Range(1, 1, position.lineNumber, position.column),
  );

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
      if (current === '\\') {
        index += 1;
        continue;
      }
      if (current === "'" && next === "'") {
        index += 1;
        continue;
      }
      if (current === "'") state = 'code';
      continue;
    }

    if (state === 'double-quote') {
      if (current === '\\') {
        index += 1;
        continue;
      }
      if (current === '"' && next === '"') {
        index += 1;
        continue;
      }
      if (current === '"') state = 'code';
      continue;
    }

    if (state === 'backtick') {
      if (current === '\\') {
        index += 1;
        continue;
      }
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
      continue;
    }
    if (current === '/' && next === '*') {
      state = 'block-comment';
      index += 1;
      continue;
    }
    if (current === "'") {
      state = 'single-quote';
      continue;
    }
    if (current === '"') {
      state = 'double-quote';
      continue;
    }
    if (current === '`') {
      state = 'backtick';
      continue;
    }
    if (current === '[') {
      state = 'bracket-identifier';
    }
  }

  return state;
};

const formatCandidateName = (name: string, prefix: string) => {
  if (prefix && prefix === prefix.toLowerCase()) return name.toLowerCase();
  return name;
};

const getCompletionRange = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
) => {
  const word = model.getWordUntilPosition(position);
  return {
    prefix: word.word,
    range: new monaco.Range(
      position.lineNumber,
      word.startColumn,
      position.lineNumber,
      word.endColumn,
    ),
  };
};

const shouldProvideCompletion = (
  model: monaco.editor.ITextModel,
  position: monaco.Position,
  wordStartColumn: number,
) => {
  const textBeforePosition = getTextBeforePosition(model, position);
  if (getLexicalState(textBeforePosition) !== 'code') return false;

  if (wordStartColumn > 1) {
    const characterBeforeWord = model.getValueInRange(
      new monaco.Range(
        position.lineNumber,
        wordStartColumn - 1,
        position.lineNumber,
        wordStartColumn,
      ),
    );
    if (characterBeforeWord === '.') return false;
  }

  return true;
};

const createProvider = () =>
  monaco.languages.registerCompletionItemProvider('sql', {
    provideCompletionItems: (model, position) => {
      const { prefix, range } = getCompletionRange(model, position);
      if (!shouldProvideCompletion(model, position, range.startColumn)) {
        return { suggestions: [] };
      }

      const keywordSuggestions: monaco.languages.CompletionItem[] =
        SQL_KEYWORDS.map((keyword) => ({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: formatCandidateName(keyword, prefix),
          filterText: keyword,
          detail: 'SQL 关键字',
          sortText: `1-${keyword}`,
          range,
        }));

      const functionSuggestions: monaco.languages.CompletionItem[] =
        SQL_BUILTIN_FUNCTIONS.map((definition) => {
          const name = formatCandidateName(definition.name, prefix);
          return {
            label: definition.name,
            kind: monaco.languages.CompletionItemKind.Function,
            insertText: `${name}(${definition.argumentsSnippet})`,
            insertTextRules:
              monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            filterText: definition.name,
            detail: definition.signature,
            documentation: definition.description,
            sortText: `2-${definition.name}`,
            range,
          };
        });

      return {
        suggestions: [...keywordSuggestions, ...functionSuggestions],
      };
    },
  });

export const acquireSqlBuiltinCompletionProvider = (): monaco.IDisposable => {
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
