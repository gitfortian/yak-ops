import WorkflowDefinitionEditor from './WorkflowDefinitionEditor';

/**
 * Workflow Definition uses its own workspace chrome (workflow sidebar + toolbar +
 * ReactFlow canvas), so it should consume the whole browser viewport when the
 * route is mounted outside SiteLayout.
 *
 * Keep the sizing override here instead of coupling the large orchestration
 * component to a specific application shell. This also keeps the existing
 * editor behavior intact while allowing immersive routes to own 100vh.
 */
export default function WorkflowDefinitionFullscreenPage() {
  return (
    <div className="workflow-definition-fullscreen-shell h-screen overflow-hidden bg-[#f2f4f7]">
      <WorkflowDefinitionEditor />
      <style>{`
        .workflow-definition-fullscreen-shell > div:first-child {
          height: 100vh !important;
          min-height: 100vh !important;
        }

        .workflow-definition-fullscreen-shell .workflow-editor-toolbar {
          height: 52px !important;
        }
      `}</style>
    </div>
  );
}
