import { BizError, handleAuthenticationFailure } from "@/service/http/request";

/** Keeps the session-expiry redirect and leaves every other failure to the caller. */
export async function governanceRequest<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof BizError && (error.code === 401 || error.code === 2001)) {
      handleAuthenticationFailure();
    }
    throw error;
  }
}

/** Returns the server business message for a governance failure, or a generic fallback. */
export function governanceFailureMessage(error: unknown, fallback = "操作失败，请稍后重试") {
  return error instanceof BizError && error.message ? error.message : fallback;
}

export function isBatchTooLarge(error: unknown) {
  return error instanceof BizError && error.code === 19801;
}

export function isInvalidIssueState(error: unknown) {
  return error instanceof BizError && error.code === 19802;
}

export function isInvalidIgnoreWindow(error: unknown) {
  return error instanceof BizError && error.code === 19804;
}

export function isScanBusy(error: unknown) {
  return error instanceof BizError && error.code === 19805;
}
