import HttpUtils from "@/service/http/HttpUtils";

import { governanceRequest } from "./request";
import type { GovernanceHealth, GovernanceOverview, GovernanceRule } from "./types";

const API = "/api/v1/governance";

export const getOverview = (signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<GovernanceOverview>(`${API}/overview`, { signal, skipErrorHandler: true }),
  );

export const getRules = (signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<GovernanceRule[]>(`${API}/rules`, { signal, skipErrorHandler: true }),
  );

export const getHealth = (from: string, to: string, signal?: AbortSignal) =>
  governanceRequest(
    HttpUtils.getData<GovernanceHealth>(
      `${API}/health?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
      { signal, skipErrorHandler: true },
    ),
  );
