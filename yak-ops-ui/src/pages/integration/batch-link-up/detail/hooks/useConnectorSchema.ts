import { useEffect, useState } from 'react';

import {
  getOfflineSyncConnectorSchema,
  type LinkUpConnectorSchema,
  type OfflineConnectorRole,
} from '@/services/batch-link-up';

export interface ConnectorSchemaState {
  schema: LinkUpConnectorSchema | null;
  loading: boolean;
  error: string;
}

export default function useConnectorSchema(
  connectorId: string,
  role: OfflineConnectorRole,
  enabled: boolean,
): ConnectorSchemaState {
  const [schema, setSchema] = useState<LinkUpConnectorSchema | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    if (!enabled || !connectorId?.trim()) {
      setSchema(null);
      setLoading(false);
      setError('');
      return () => {
        active = false;
      };
    }

    setLoading(true);
    setError('');

    void getOfflineSyncConnectorSchema(connectorId, role)
      .then((value) => {
        if (!active) return;
        setSchema(value);
      })
      .catch((cause: any) => {
        if (!active) return;
        setSchema(null);
        setError(cause?.message || `无法读取 ${role} Connector Schema`);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [connectorId, enabled, role]);

  return { schema, loading, error };
}
