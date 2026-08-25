export type ApiProtocol = 'yak-ops' | 'security';

export const API_SUCCESS_CODE = 200;

export interface ApiResponse<T = unknown> {
  code: number;
  data: T;
  msg?: string;
  message?: string;
}

const protocolRules: Record<
  ApiProtocol,
  { success: readonly number[]; unauthenticated: readonly number[] }
> = {
  'yak-ops': {
    success: [API_SUCCESS_CODE],
    unauthenticated: [401],
  },
  security: {
    success: [API_SUCCESS_CODE],
    unauthenticated: [401, 2001],
  },
};

const unauthenticatedMessages = new Set([
  'NOT_LOGIN',
  'UNAUTHENTICATED',
  'SESSION_EXPIRED',
  'SESSION_INVALID',
  'TOKEN_EXPIRED',
  'TOKEN_INVALID',
  'LOGIN_EXPIRED',
]);

const normalizeUnauthenticatedMessage = (message: string) =>
  message.trim().toUpperCase().replace(/[\s-]+/g, '_');

export const protocolForUrl = (url?: string): ApiProtocol =>
  url?.includes('/yak-security/') ? 'security' : 'yak-ops';

export const isApiResponse = (value: unknown): value is ApiResponse => {
  if (!value || typeof value !== 'object') return false;
  return typeof (value as Partial<ApiResponse>).code === 'number';
};

export const isSuccessfulResponse = (
  response: Partial<ApiResponse> | null | undefined,
  protocol: ApiProtocol,
): boolean =>
  typeof response?.code === 'number' &&
  protocolRules[protocol].success.includes(response.code);

export const extractErrorMessage = (
  response: Partial<ApiResponse> | null | undefined,
  fallback = '操作失败',
): string => response?.msg?.trim() || response?.message?.trim() || fallback;

/**
 * Extract a useful server error from both the standard API envelope and common
 * HTTP error payloads. This prevents a real backend reason from being replaced
 * by a generic 4xx/5xx status description in the request error handler.
 */
export const extractUnknownErrorMessage = (
  payload: unknown,
  fallback = '请求失败',
): string => {
  if (isApiResponse(payload)) {
    return extractErrorMessage(payload, fallback);
  }

  if (typeof payload === 'string' && payload.trim()) {
    return payload.trim();
  }

  if (payload && typeof payload === 'object') {
    const source = payload as Record<string, unknown>;
    for (const key of ['msg', 'message', 'error', 'detail']) {
      const value = source[key];
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
  }

  return fallback;
};

export const isUnauthenticatedResponse = (
  response: Partial<ApiResponse> | null | undefined,
  protocol: ApiProtocol,
): boolean => {
  const message = response?.msg || response?.message;
  return (
    (typeof response?.code === 'number' &&
      protocolRules[protocol].unauthenticated.includes(response.code)) ||
    (typeof message === 'string' &&
      unauthenticatedMessages.has(normalizeUnauthenticatedMessage(message)))
  );
};
