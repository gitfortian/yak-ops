package io.yak.ops.common.bean.vo.modeling;

import java.util.List;

/**
 * Publication plan for one explicit target, before anything is executed.
 *
 * <p>{@code operation} tells the three outcomes apart: {@code CREATE} when the table does not
 * exist, {@code ALTER} when a diff was translated into change statements, and {@code NONE} when
 * the physical table already matches the model — {@code ddl} is null there because nothing would
 * run.
 *
 * @param database resolved target database
 * @param schema resolved target schema, null where the engine has none
 * @param table physical table name, which is the model name
 * @param operation CREATE, ALTER or NONE
 * @param changes schema diff, ordered add, change, drop
 * @param ddl complete script, statements separated by a semicolon and a newline; null when nothing runs
 * @param requiresDataLossAck true when the plan drops columns and publishing needs the explicit acknowledgement
 */
public record PublicationPreviewVO(
        String database,
        String schema,
        String table,
        String operation,
        List<SchemaChangeVO> changes,
        String ddl,
        boolean requiresDataLossAck) {}
