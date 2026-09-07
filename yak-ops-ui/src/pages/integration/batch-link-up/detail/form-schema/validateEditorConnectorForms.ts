import {
  getOfflineSyncConnectorRuntime,
  getOfflineSyncConnectorSchema,
} from '@/services/batch-link-up';

import { validateEditorCapabilities } from '../capabilities';
import type { SyncEditorState } from '../model';
import { validateRequiredConnectorTaskOptions } from './connectorTaskOptions';

/**
 * Save-time Connector validation uses the same runtime truth as the editor.
 *
 * A temporarily unreachable Worker does not prevent saving a draft/config update: Yak Ops may keep
 * stale capability metadata for display, but it is not authoritative while unreachable. When the
 * Worker is reachable, role availability and declared capabilities are strict and full schemas are
 * fetched live before validating task-owned required options.
 */
export default async function validateEditorConnectorForms(
  editor: SyncEditorState,
): Promise<string[]> {
  let runtime;

  try {
    runtime = await getOfflineSyncConnectorRuntime();
  } catch (error: any) {
    return [
      error?.message || '无法读取 Link-Up Connector 能力，请稍后重试',
    ];
  }

  const capabilityErrors = validateEditorCapabilities(editor, runtime);
  if (capabilityErrors.length > 0) {
    return capabilityErrors;
  }

  if (!runtime.reachable) {
    return [];
  }

  const [sourceSchema, sinkSchema] = await Promise.allSettled([
    getOfflineSyncConnectorSchema(editor.source.connectorId, 'SOURCE'),
    getOfflineSyncConnectorSchema(editor.sink.connectorId, 'SINK'),
  ]);
  const errors: string[] = [];

  if (sourceSchema.status === 'rejected') {
    errors.push(
      sourceSchema.reason?.message || '无法读取 Source Connector Schema',
    );
  } else {
    errors.push(
      ...validateRequiredConnectorTaskOptions(
        sourceSchema.value,
        'SOURCE',
        editor.source.config,
      ),
    );
  }

  if (sinkSchema.status === 'rejected') {
    errors.push(
      sinkSchema.reason?.message || '无法读取 Sink Connector Schema',
    );
  } else {
    errors.push(
      ...validateRequiredConnectorTaskOptions(
        sinkSchema.value,
        'SINK',
        editor.sink.config,
      ),
    );
  }

  return errors;
}
