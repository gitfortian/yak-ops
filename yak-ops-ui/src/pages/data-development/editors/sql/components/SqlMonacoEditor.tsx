import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution';
import { useEffect, useRef } from 'react';

import { setupMonacoEnvironment } from '../monaco/setupMonacoEnvironment';

export interface SqlEditorPosition {
  lineNumber: number;
  column: number;
  selectionLength: number;
}

interface SqlMonacoEditorProps {
  id: string;
  value: string;
  onChange: (value: string) => void;
  onPositionChange?: (position: SqlEditorPosition) => void;
}

let themeRegistered = false;

const ensureYakSqlTheme = () => {
  if (themeRegistered) return;
  themeRegistered = true;

  monaco.editor.defineTheme('yak-sql-light', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'keyword.sql', foreground: '245BDB', fontStyle: 'bold' },
      { token: 'string.sql', foreground: '027A48' },
      { token: 'number.sql', foreground: 'B54708' },
      { token: 'comment.sql', foreground: '98A2B3', fontStyle: 'italic' },
    ],
    colors: {
      'editor.background': '#ffffff',
      'editor.foreground': '#344054',
      'editorLineNumber.foreground': '#b0b7c3',
      'editorLineNumber.activeForeground': '#667085',
      'editorCursor.foreground': '#344054',
      'editor.selectionBackground': '#dbeafe',
      'editor.inactiveSelectionBackground': '#eef4ff',
      'editor.lineHighlightBackground': '#fafafa',
      'editorIndentGuide.background1': '#f0f1f3',
      'editorIndentGuide.activeBackground1': '#d0d5dd',
    },
  });
};

const SqlMonacoEditor = ({
  id,
  value,
  onChange,
  onPositionChange,
}: SqlMonacoEditorProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor>();
  const modelRef = useRef<monaco.editor.ITextModel>();
  const onChangeRef = useRef(onChange);
  const onPositionChangeRef = useRef(onPositionChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    onPositionChangeRef.current = onPositionChange;
  }, [onPositionChange]);

  useEffect(() => {
    if (!containerRef.current) return undefined;

    setupMonacoEnvironment();
    ensureYakSqlTheme();

    const uri = monaco.Uri.parse(
      `inmemory://yak-ops/data-development/sql/${encodeURIComponent(id)}.sql`,
    );
    monaco.editor.getModel(uri)?.dispose();

    const model = monaco.editor.createModel(value, 'sql', uri);
    const editor = monaco.editor.create(containerRef.current, {
      model,
      theme: 'yak-sql-light',
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineHeight: 22,
      fontFamily:
        "'JetBrains Mono', 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace",
      tabSize: 2,
      insertSpaces: true,
      scrollBeyondLastLine: false,
      smoothScrolling: true,
      cursorSmoothCaretAnimation: 'on',
      cursorBlinking: 'smooth',
      renderLineHighlight: 'line',
      renderWhitespace: 'selection',
      wordWrap: 'off',
      folding: true,
      glyphMargin: false,
      lineNumbersMinChars: 3,
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      fixedOverflowWidgets: true,
      padding: { top: 12, bottom: 12 },
      suggest: { showWords: false },
      wordBasedSuggestions: 'off',
      bracketPairColorization: { enabled: true },
      guides: { bracketPairs: true, indentation: true },
    });

    editorRef.current = editor;
    modelRef.current = model;

    const emitPosition = () => {
      const position = editor.getPosition();
      const selection = editor.getSelection();
      if (!position) return;

      const selectionLength = selection
        ? model.getValueInRange(selection).length
        : 0;
      onPositionChangeRef.current?.({
        lineNumber: position.lineNumber,
        column: position.column,
        selectionLength,
      });
    };

    const contentDisposable = model.onDidChangeContent(() => {
      onChangeRef.current(model.getValue());
    });
    const cursorDisposable = editor.onDidChangeCursorPosition(emitPosition);
    const selectionDisposable = editor.onDidChangeCursorSelection(emitPosition);

    emitPosition();

    return () => {
      contentDisposable.dispose();
      cursorDisposable.dispose();
      selectionDisposable.dispose();
      editor.dispose();
      model.dispose();
      editorRef.current = undefined;
      modelRef.current = undefined;
    };
  }, [id]);

  useEffect(() => {
    const model = modelRef.current;
    if (!model || model.getValue() === value) return;

    model.pushEditOperations(
      [],
      [{ range: model.getFullModelRange(), text: value }],
      () => null,
    );
  }, [value]);

  return <div ref={containerRef} className="h-full min-h-0 w-full" />;
};

export default SqlMonacoEditor;
