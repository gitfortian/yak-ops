import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { useAuth } from "@/hooks/use-auth";
import { listUsers, type UserPageParams } from "@/service/user";

export const userQueryKeys = {
  all: ["management", "users"] as const,
  page: (userId: string, params: UserPageParams) =>
    [
      ...userQueryKeys.all,
      userId,
      params.pageNo,
      params.pageSize,
      params.userName ?? "",
      params.realName ?? "",
    ] as const,
};

export function useUsers(params: UserPageParams) {
  const { currentUser } = useAuth();
  const [search, setSearch] = useState({
    userName: params.userName?.trim() ?? "",
    realName: params.realName?.trim() ?? "",
  });

  useEffect(() => {
    const next = {
      userName: params.userName?.trim() ?? "",
      realName: params.realName?.trim() ?? "",
    };
    const delay = next.userName || next.realName ? 300 : 0;
    const timer = window.setTimeout(() => setSearch(next), delay);
    return () => window.clearTimeout(timer);
  }, [params.userName, params.realName]);

  const normalized = {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    userName: search.userName || undefined,
    realName: search.realName || undefined,
  };
  const userId = currentUser?.id ?? "";
  return useQuery({
    queryKey: userQueryKeys.page(userId, normalized),
    queryFn: () => listUsers(normalized),
    enabled: Boolean(userId),
  });
}
