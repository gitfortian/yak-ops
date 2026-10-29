import { BizError, handleAuthenticationFailure } from "@/service/http/request";

/** Keeps the session-expiry redirect and leaves every other failure to the caller. */
export async function dataMapRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

/** Returns the server business message for a data map failure, or a generic fallback. */
export function dataMapFailureMessage(error: unknown, fallback = "操作失败，请稍后重试") {
  return error instanceof BizError && error.message ? error.message : fallback;
}

export function isPreviewBusy(error: unknown) {
  return error instanceof BizError && error.code === 19711;
}

export function isSearchTooFrequent(error: unknown) {
  return error instanceof BizError && error.code === 19710;
}

export function isCronExpressionInvalid(error: unknown) {
  return error instanceof BizError && error.code === 19718;
}
