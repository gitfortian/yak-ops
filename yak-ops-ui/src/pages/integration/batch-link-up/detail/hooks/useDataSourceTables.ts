import { useCallback, useEffect, useState } from 'react';

import { dataSourceCatalogApi } from '@/pages/data-source/service';

const TABLE_SEARCH_DEBOUNCE_MS = 250;
const TABLE_SEARCH_LIMIT = 100;

const normalizeTableNames = (data: any): string[] => {
  const values = Array.isArray(data)
    ? data
    : Array.isArray(data?.bizData)
      ? data.bizData
      : Array.isArray(data?.records)
        ? data.records
        : [];

  return Array.from(
    new Set(
      values
        .map((item: any) =>
          typeof item === 'string'
            ? item
            : item?.name ||
              item?.value ||
              item?.label,
        )
        .filter(Boolean)
        .map(String),
    ),
  );
};

/**
 * Loads only a bounded table window and delegates filtering to the backend.
 *
 * Large hospital / warehouse catalogs can contain thousands of tables. Keeping the search term in
 * this hook avoids materializing the complete catalog in the browser while preserving the selected
 * value in the editor state.
 */
export default function useDataSourceTables(dataSourceId: string) {
  const [tables, setTables] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    setKeyword('');
    setTables([]);
  }, [dataSourceId]);

  useEffect(() => {
    if (!dataSourceId) {
      setTables([]);
      setLoading(false);
      return undefined;
    }

    let active = true;
    setLoading(true);

    const timer = window.setTimeout(() => {
      dataSourceCatalogApi
        .searchTables(
          dataSourceId,
          keyword.trim() || undefined,
          TABLE_SEARCH_LIMIT,
        )
        .then((response) => {
          if (!active) return;
          setTables(normalizeTableNames(response?.data));
        })
        .catch(() => {
          if (active) {
            setTables([]);
          }
        })
        .finally(() => {
          if (active) {
            setLoading(false);
          }
        });
    }, TABLE_SEARCH_DEBOUNCE_MS);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [dataSourceId, keyword]);

  const search = useCallback((value: string) => {
    setKeyword(value);
  }, []);

  return {
    tables,
    loading,
    search,
  };
}
