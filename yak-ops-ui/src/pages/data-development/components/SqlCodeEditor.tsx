import { autocompletion } from '@codemirror/autocomplete';
import {
  defaultKeymap,
  history,
  historyKeymap,
} from '@codemirror/commands';
import { sql } from '@codemirror/lang-sql';
import {
  bracketMatching,
  defaultHighlightStyle,
  syntaxHighlighting,
} from '@codemirror/language';
import { EditorState } from '@codemirror/state';
import {
  EditorView,
  highlightActiveLine,
  highlightActiveLineGutter,
  keymap,
  lineNumbers,
} from '@codemirror/view';
import { useEffect, useRef } from 'react';

interface SqlCodeEditorProps {
  value: string;
  onChange: (value: string) => void;
  minHeight?: number;
}

export default function SqlCodeEditor({
  value,
  onChange,
  minHeight = 360,
}: SqlCodeEditorProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView>();
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    if (!hostRef.current) return undefined;

    const state = EditorState.create({
      doc: value,
      extensions: [
        lineNumbers(),
        highlightActiveLine(),
        highlightActiveLineGutter(),
        history(),
        bracketMatching(),
        autocompletion(),
        sql(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        keymap.of([...defaultKeymap, ...historyKeymap]),
        EditorView.lineWrapping,
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            onChangeRef.current(update.state.doc.toString());
          }
        }),
        EditorView.theme({
          '&': {
            minHeight: `${minHeight}px`,
            height: '100%',
            fontSize: '13px',
            backgroundColor: '#fff',
          },
          '.cm-scroller': {
            minHeight: `${minHeight}px`,
            fontFamily:
              'JetBrains Mono, SFMono-Regular, Consolas, Liberation Mono, monospace',
            lineHeight: '1.7',
          },
          '.cm-gutters': {
            backgroundColor: '#fafafa',
            borderRight: '1px solid #f0f0f0',
          },
          '.cm-activeLine': {
            backgroundColor: 'rgba(0, 0, 0, 0.025)',
          },
          '.cm-activeLineGutter': {
            backgroundColor: 'rgba(0, 0, 0, 0.035)',
          },
          '&.cm-focused': {
            outline: 'none',
          },
        }),
      ],
    });

    const view = new EditorView({ state, parent: hostRef.current });
    viewRef.current = view;

    return () => {
      view.destroy();
      viewRef.current = undefined;
    };
  }, [minHeight]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current === value) return;
    view.dispatch({
      changes: { from: 0, to: current.length, insert: value },
    });
  }, [value]);

  return (
    <div
      ref={hostRef}
      className="overflow-hidden rounded-lg border border-[#e4e7ec] bg-white"
    />
  );
}
