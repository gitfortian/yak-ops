package io.yak.ops.common.bean.vo.modeling;

/**
 * Generated DDL for one explicit target, before anything is executed.
 *
 * @param database resolved target database
 * @param schema resolved target schema, null where the engine has none
 * @param table physical table name, which is the model name
 * @param ddl complete script, statements separated by a semicolon and a newline
 */
public record PublicationPreviewVO(String database, String schema, String table, String ddl) {}
