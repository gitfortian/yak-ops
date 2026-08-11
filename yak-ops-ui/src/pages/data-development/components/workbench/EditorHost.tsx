import type { DevelopmentEditorDefinition } from '../../editors/types';
import type { DevelopmentDirectory, DevelopmentNode } from '../../types';

interface EditorHostProps {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
}

const EditorHost = ({ node, directory, definition }: EditorHostProps) => {
  const Editor = definition.Editor;

  return (
    <section className="min-w-0 flex-1 overflow-hidden bg-white">
      <Editor key={String(node.id)} node={node} directory={directory} />
    </section>
  );
};

export default EditorHost;
