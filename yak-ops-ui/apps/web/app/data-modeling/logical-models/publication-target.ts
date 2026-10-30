/**
 * Client-side mirror of the publish-request rules the endpoint enforces.
 *
 * <p>The user names an environment only: the datasource, database and schema come from the target
 * the model's layer binds for that environment, so the client has no address to validate. What is
 * still validated here is the environment choice itself and the reachability of the engine behind
 * the bound datasource. Keeping the rules in a zero-import module lets them be tested directly
 * instead of only through a rendered dialog.
 */
import { isJdbcEngine } from "../shared/jdbc-engines.ts";

/** One rejected request, keyed to the control the user must correct. */
export interface TargetProblem {
  field: "environment" | "engine";
  message: string;
}

export const PUBLISH_ENVIRONMENTS = ["DEVELOP", "PROD"] as const;
export type PublishEnvironment = (typeof PUBLISH_ENVIRONMENTS)[number];

export const PUBLISH_ENVIRONMENT_LABEL: Record<PublishEnvironment, string> = {
  DEVELOP: "开发",
  PROD: "生产",
};

/** Submits the request in the shape the endpoint expects. */
export function toTargetPayload(environment: string, acknowledgeDataLoss?: boolean) {
  return { environment, acknowledgeDataLoss: Boolean(acknowledgeDataLoss) };
}

/**
 * Validates one publish request before any round trip.
 *
 * @param environment environment the user picked, empty when none
 * @param dbType dbType of the datasource the layer binding resolves to, null while loading
 */
export function validatePublicationTarget(
  environment: string,
  dbType?: string | null,
): TargetProblem | null {
  if (!PUBLISH_ENVIRONMENTS.includes(environment as PublishEnvironment)) {
    return { field: "environment", message: "请选择发布环境（开发或生产）" };
  }
  if (!isJdbcEngine(dbType)) {
    return {
      field: "engine",
      message: "该数据源类型不支持物理发布，请检查分层绑定的目标数据源",
    };
  }
  return null;
}
