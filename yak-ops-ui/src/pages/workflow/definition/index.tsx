import WorkflowDefinitionEditor from './WorkflowDefinitionEditor';

/**
 * Workflow Definition owns a full-screen editor shell. The layout deliberately
 * follows a professional canvas editor rather than a workflow-product sidebar:
 * one global toolbar, a compact asset rail, canvas workspace and docked inspector.
 */
export default function WorkflowDefinitionFullscreenPage() {
  return (
    <div className="workflow-definition-fullscreen-shell h-screen overflow-hidden bg-[#f5f6f8]">
      <WorkflowDefinitionEditor />
      <style>{`
        .workflow-definition-fullscreen-shell > div:first-child {
          height: 100vh !important;
          min-height: 100vh !important;
          padding-top: 52px;
          box-sizing: border-box;
          background: #f5f6f8 !important;
        }

        .workflow-definition-fullscreen-shell .workflow-editor-toolbar {
          position: fixed !important;
          inset: 0 0 auto 0 !important;
          z-index: 60 !important;
          width: 100vw !important;
          height: 52px !important;
          border-bottom: 1px solid #e8eaee !important;
          background: rgba(255,255,255,.98) !important;
          padding-left: 18px !important;
          padding-right: 14px !important;
        }

        .workflow-definition-fullscreen-shell section > div[style],
        .workflow-definition-fullscreen-shell section > div.relative {
          background: #f5f6f8 !important;
        }

        .workflow-definition-fullscreen-shell .react-flow {
          background: #f7f8fa !important;
        }

        .workflow-definition-fullscreen-shell .react-flow__background pattern circle {
          fill: #dfe3e8 !important;
          opacity: .7;
        }

        .workflow-definition-fullscreen-shell .react-flow__minimap {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside {
          top: 0 !important;
          right: 0 !important;
          bottom: 0 !important;
          left: auto !important;
          width: 340px !important;
          border: 0 !important;
          border-left: 1px solid #e8eaee !important;
          border-radius: 0 !important;
          box-shadow: none !important;
          background: #fff !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > .react-flow {
          width: calc(100% - 340px) !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header {
          border-bottom: 1px solid #eef0f2 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header > div:nth-child(2) {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header > div:first-child {
          padding: 14px 16px 8px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header nav {
          height: 38px !important;
          padding-left: 16px !important;
          padding-right: 16px !important;
          gap: 20px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside .mx-4.border-t {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside section {
          padding-top: 13px !important;
          padding-bottom: 13px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside .rounded-xl {
          border-radius: 8px !important;
        }

        .workflow-definition-fullscreen-shell [class*="absolute left-3 top-1/2"] {
          top: 12px !important;
          left: 50% !important;
          bottom: auto !important;
          transform: translateX(-50%) !important;
          flex-direction: row !important;
          border-radius: 9px !important;
          box-shadow: 0 2px 10px rgba(22,24,35,.06) !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) [class*="absolute left-3 top-1/2"] {
          left: calc((100% - 340px) / 2) !important;
        }

        .workflow-definition-fullscreen-shell [class*="absolute bottom-2 left-1/2"] {
          bottom: 14px !important;
          border-radius: 9px !important;
          box-shadow: 0 2px 10px rgba(22,24,35,.06) !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) [class*="absolute bottom-2 left-1/2"] {
          left: calc((100% - 340px) / 2) !important;
        }

        .workflow-definition-fullscreen-shell [class*="absolute left-3 top-1/2"] > div[class*="h-px"] {
          width: 1px !important;
          height: 18px !important;
          margin: 0 4px !important;
        }

        @media (max-width: 1180px) {
          .workflow-definition-fullscreen-shell section > div:has(> aside) > aside {
            width: 320px !important;
          }

          .workflow-definition-fullscreen-shell section > div:has(> aside) > .react-flow {
            width: calc(100% - 320px) !important;
          }

          .workflow-definition-fullscreen-shell section > div:has(> aside) [class*="absolute left-3 top-1/2"],
          .workflow-definition-fullscreen-shell section > div:has(> aside) [class*="absolute bottom-2 left-1/2"] {
            left: calc((100% - 320px) / 2) !important;
          }
        }
      `}</style>
    </div>
  );
}
