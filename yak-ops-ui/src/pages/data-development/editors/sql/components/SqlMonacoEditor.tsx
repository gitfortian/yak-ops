import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import { useEffect, useRef } from 'react';

import type { DevelopmentEditorViewState } from '../../session/types';
import { acquireSqlHoverProvider } from '../assistance/registerSqlHover';
import { acquireSqlSignatureHelpProvider } from '../assistance/registerSqlSignatureHelp';
import { registerSqlEditorCommandHandler } from '../commands/sqlEditorCommandBus';
import { acquireSqlBuiltinCompletionProvider } from '../completion/registerSqlBuiltinCompletion';
import {
  acquireSqlMetadataCompletionProvider,
  bindSqlMetadataModel,
} from '../completion/registerSqlMetadataCompletion';
import { acquireSqlSnippetCompletionProvider } from '../completion/registerSqlSnippetCompletion';
import { bindSqlDiagnostics } from '../diagnostics/bindSqlDiagnostics';
import {
  YAK_EDITOR_THEMES,
  editorOptionsFromSettings,
  getYakEditorSettings,
  subscribeYakEditorSettings,
} from '../editorSettings';
import { formatSqlText } from '../formatting/formatSqlText';
import { setupMonacoEnvironment } from '../monaco/setupMonacoEnvironment';
import { getSqlStatementRanges } from '../monaco/sqlStatementRanges';

export interface SqlEditorPosition {
  lineNumber: number;
  column: number;
  selectionLength: number;
}

interface SqlMonacoEditorProps {
  id: string;
  value: string;
  initialViewState?: DevelopmentEditorViewState;
  onChange: (value: string) => void;
  onRunStatement?: (sql: string) => void;
  running?: boolean;
  readOnly?: boolean;
  onPositionChange?: (position: SqlEditorPosition) => void;
  onViewStateChange?: (viewState: DevelopmentEditorViewState) => void;
}

let themesRegistered = false;

const ensureYakThemes = () => {
  if (themesRegistered) return;
  themesRegistered = true;
  YAK_EDITOR_THEMES.forEach((theme) => monaco.editor.defineTheme(theme.name, theme.data));
};

const SqlMonacoEditor = ({
  id,
  value,
  initialViewState,
  onChange,
  onRunStatement,
  running,
  readOnly = false,
  onPositionChange,
  onViewStateChange,
}: SqlMonacoEditorProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor>();
  const modelRef = useRef<monaco.editor.ITextModel>();
  const onChangeRef = useRef(onChange);
  const onRunStatementRef = useRef(onRunStatement);
  const runningRef = useRef(running);
  const onPositionChangeRef = useRef(onPositionChange);
  const onViewStateChangeRef = useRef(onViewStateChange);

  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);
  useEffect(() => { onRunStatementRef.current = onRunStatement; }, [onRunStatement]);
  useEffect(() => { runningRef.current = running; }, [running]);
  useEffect(() => { onPositionChangeRef.current = onPositionChange; }, [onPositionChange]);
  useEffect(() => { onViewStateChangeRef.current = onViewStateChange; }, [onViewStateChange]);

  useEffect(() => {
    if (!containerRef.current) return undefined;

    setupMonacoEnvironment();
    ensureYakThemes();
    const builtinCompletionProvider = acquireSqlBuiltinCompletionProvider();
    const metadataCompletionProvider = acquireSqlMetadataCompletionProvider();
    const snippetCompletionProvider = acquireSqlSnippetCompletionProvider();
    const hoverProvider = acquireSqlHoverProvider();
    const signatureHelpProvider = acquireSqlSignatureHelpProvider();
    const foldingProvider = monaco.languages.registerFoldingRangeProvider('sql', {
      provideFoldingRanges: (foldingModel) =>
        getSqlStatementRanges(foldingModel.getValue())
          .filter((statement) => statement.endLine > statement.startLine)
          .map((statement) => ({ start: statement.startLine, end: statement.endLine })),
    });

    const uri = monaco.Uri.parse(`inmemory://yak-ops/data-development/sql/${encodeURIComponent(id)}.sql`);
    monaco.editor.getModel(uri)?.dispose();

    const model = monaco.editor.createModel(value, 'sql', uri);
    const metadataModelBinding = bindSqlMetadataModel(model.uri.toString(), id);
    const diagnosticsBinding = bindSqlDiagnostics(model);
    const initialSettings = getYakEditorSettings();
    const editor = monaco.editor.create(containerRef.current, {
      model,
      ...editorOptionsFromSettings(initialSettings),
      readOnly,
      domReadOnly: readOnly,
      automaticLayout: true,
      tabSize: 2,
      insertSpaces: true,
      scrollBeyondLastLine: false,
      smoothScrolling: true,
      cursorSmoothCaretAnimation: 'on',
      cursorBlinking: 'smooth',
      showFoldingControls: 'always',
      glyphMargin: !readOnly,
      lineNumbersMinChars: 3,
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      fixedOverflowWidgets: true,
      padding: { top: 12, bottom: 12 },
      quickSuggestions: { other: true, comments: false, strings: false },
      suggestOnTriggerCharacters: true,
      acceptSuggestionOnEnter: 'on',
      tabCompletion: 'on',
      suggestSelection: 'recentlyUsedByPrefix',
      parameterHints: { enabled: true },
      hover: { enabled: 'on', delay: 300, sticky: true },
      suggest: {
        showWords: false,
        showKeywords: true,
        showFunctions: true,
        showFields: true,
        showStructs: true,
        showInterfaces: true,
        showSnippets: true,
        snippetsPreventQuickSuggestions: false,
      },
      wordBasedSuggestions: 'off',
      bracketPairColorization: { enabled: true },
      guides: { bracketPairs: true, indentation: true },
    });

    editorRef.current = editor;
    modelRef.current = model;

    const unsubscribeSettings = subscribeYakEditorSettings((settings) => {
      monaco.editor.setTheme(settings.theme);
      editor.updateOptions(editorOptionsFromSettings(settings));
    });

    let statementRanges = getSqlStatementRanges(model.getValue());
    const runDecorations = editor.createDecorationsCollection();
    const refreshStatementDecorations = () => {
      statementRanges = getSqlStatementRanges(model.getValue());
      runDecorations.set(
        readOnly
          ? []
          : statementRanges.map((statement) => ({
              range: new monaco.Range(statement.startLine, 1, statement.startLine, 1),
              options: { glyphMarginClassName: 'yak-sql-run-glyph' },
            })),
      );
    };
    refreshStatementDecorations();

    let wordWrapEnabled = initialSettings.wordWrap;
    let minimapEnabled = initialSettings.showMinimap;
    const commandBinding = registerSqlEditorCommandHandler(id, async (command) => {
      editor.focus();
      if (command === 'undo' || command === 'redo') {
        if (!readOnly) editor.trigger('yak-sql-toolbar', command, null);
        return;
      }
      if (command === 'find') {
        await editor.getAction('actions.find')?.run();
        return;
      }
      if (command === 'suggest') {
        if (!readOnly) await editor.getAction('editor.action.triggerSuggest')?.run();
        return;
      }
      if (command === 'toggle-word-wrap') {
        wordWrapEnabled = !wordWrapEnabled;
        editor.updateOptions({ wordWrap: wordWrapEnabled ? 'on' : 'off' });
        return;
      }
      if (command === 'toggle-minimap') {
        minimapEnabled = !minimapEnabled;
        editor.updateOptions({ minimap: { enabled: minimapEnabled } });
        return;
      }
      if (command === 'format' && !readOnly) {
        const formatted = formatSqlText(model.getValue());
        if (formatted === model.getValue()) return;
        editor.pushUndoStop();
        editor.executeEdits('yak-sql-format', [{ range: model.getFullModelRange(), text: formatted, forceMoveMarkers: true }]);
        editor.pushUndoStop();
      }
    });

    if (initialViewState) {
      if (initialViewState.selection) editor.setSelection(initialViewState.selection);
      else editor.setPosition({ lineNumber: initialViewState.lineNumber, column: initialViewState.column });
      editor.setScrollTop(initialViewState.scrollTop);
      editor.setScrollLeft(initialViewState.scrollLeft);
    }

    const emitEditorState = () => {
      const position = editor.getPosition();
      const selection = editor.getSelection();
      if (!position) return;
      const selectionLength = selection ? model.getValueInRange(selection).length : 0;
      onPositionChangeRef.current?.({ lineNumber: position.lineNumber, column: position.column, selectionLength });
      onViewStateChangeRef.current?.({
        lineNumber: position.lineNumber,
        column: position.column,
        selection: selection ? {
          startLineNumber: selection.startLineNumber,
          startColumn: selection.startColumn,
          endLineNumber: selection.endLineNumber,
          endColumn: selection.endColumn,
        } : undefined,
        scrollTop: editor.getScrollTop(),
        scrollLeft: editor.getScrollLeft(),
      });
    };

    const contentDisposable = model.onDidChangeContent(() => {
      refreshStatementDecorations();
      if (!readOnly) onChangeRef.current(model.getValue());
    });
    const gutterDisposable = editor.onMouseDown((event) => {
      if (
        readOnly ||
        event.target.type !== monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN ||
        runningRef.current ||
        !onRunStatementRef.current
      ) return;
      const lineNumber = event.target.position?.lineNumber;
      if (!lineNumber) return;
      const statement = statementRanges.find((candidate) => candidate.startLine === lineNumber);
      if (statement) onRunStatementRef.current(statement.sql);
    });
    const cursorDisposable = editor.onDidChangeCursorPosition(emitEditorState);
    const selectionDisposable = editor.onDidChangeCursorSelection(emitEditorState);
    const scrollDisposable = editor.onDidScrollChange(emitEditorState);

    emitEditorState();

    return () => {
      emitEditorState();
      unsubscribeSettings();
      contentDisposable.dispose();
      gutterDisposable.dispose();
      cursorDisposable.dispose();
      selectionDisposable.dispose();
      scrollDisposable.dispose();
      commandBinding.dispose();
      diagnosticsBinding.dispose();
      metadataModelBinding.dispose();
      foldingProvider.dispose();
      signatureHelpProvider.dispose();
      hoverProvider.dispose();
      snippetCompletionProvider.dispose();
      metadataCompletionProvider.dispose();
      builtinCompletionProvider.dispose();
      editor.dispose();
      model.dispose();
      editorRef.current = undefined;
      modelRef.current = undefined;
    };
  }, [id, readOnly]);

  useEffect(() => {
    const model = modelRef.current;
    if (!model || model.getValue() === value) return;
    model.pushEditOperations([], [{ range: model.getFullModelRange(), text: value }], () => null);
  }, [value]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
};

export default SqlMonacoEditor;
