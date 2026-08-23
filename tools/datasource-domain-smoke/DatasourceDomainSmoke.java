import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.ReadMode;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest.Variable;
import io.yak.ops.business.datasource.execution.domain.SqlExecutionAggregate;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementClassification;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import java.util.List;
import java.util.Set;

/** Framework-free smoke test for Phase 3 Datasource domain contracts. */
public final class DatasourceDomainSmoke {

  private DatasourceDomainSmoke() {}

  public static void main(String[] args) {
    CatalogReadRequest catalog =
        new CatalogReadRequest(
            ReadMode.SQL,
            null,
            "select * from orders where day = ${day}",
            List.of(new Variable("day", "2026-08-23")));
    require(catalog.sqlMode(), "Catalog SQL mode was lost");
    require(catalog.variables().size() == 1, "Catalog variable projection was lost");

    SqlExecutionPlan plan =
        new SqlExecutionPlan(
            "42",
            List.of(
                new SqlStatementRequest("select 1", 10, 5),
                new SqlStatementRequest("update demo set enabled = 1", 10, 5)),
            SqlExecutionContext.of(SqlExecutionCaller.CONSOLE, "smoke"));
    SqlExecutionAggregate execution =
        new SqlExecutionAggregate(
            "sql-smoke",
            plan,
            List.of(
                classification(SqlStatementType.SELECT),
                classification(SqlStatementType.UPDATE)));

    require(execution.snapshot().status() == SqlExecutionStatus.PENDING, "Execution must start PENDING");
    execution.markRunning();
    execution.markStatementRunning(0);
    execution.markStatementFailed(0, "smoke failure");
    execution.finishFailed(1, "smoke failure");

    require(execution.snapshot().status() == SqlExecutionStatus.FAILED, "Execution failure terminal state was lost");
    require(
        execution.snapshot().statements().get(0).status() == SqlStatementStatus.FAILED,
        "Failed statement state was lost");
    require(
        execution.snapshot().statements().get(1).status() == SqlStatementStatus.SKIPPED,
        "Remaining statement must be SKIPPED");

    System.out.println("Datasource Phase 3 domain smoke: OK");
  }

  private static SqlStatementClassification classification(SqlStatementType type) {
    return new SqlStatementClassification(type, Set.of(type));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
