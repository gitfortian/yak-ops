import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { parseTaskListFilters, updateTaskListFilters, type TaskListFilters } from "./filters";

export function useTaskListFilters() {
  const [search, setSearch] = useSearchParams();
  const filters = parseTaskListFilters(search);
  const [keywordInput, setKeywordInput] = useState(filters.keyword);

  useEffect(() => setKeywordInput(filters.keyword), [filters.keyword]);

  const update = useCallback(
    (patch: Partial<TaskListFilters>) =>
      setSearch((previous) => updateTaskListFilters(previous, patch), { replace: true }),
    [setSearch],
  );

  useEffect(() => {
    if (keywordInput.trim() === filters.keyword) return;
    const timer = window.setTimeout(() => update({ keyword: keywordInput, pageNo: 1 }), 250);
    return () => window.clearTimeout(timer);
  }, [filters.keyword, keywordInput, update]);

  return { ...filters, keywordInput, setKeywordInput, update };
}
