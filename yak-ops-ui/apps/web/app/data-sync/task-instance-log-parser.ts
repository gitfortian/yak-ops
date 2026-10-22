export type LogLevel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL";

export type LogTokenKind = "text" | "key" | "value" | "exception";

export interface LogToken {
  text: string;
  kind: LogTokenKind;
}

export interface ParsedLogLine {
  raw: string;
  kind: "structured" | "stack" | "plain";
  timestamp?: string;
  level?: LogLevel;
  thread?: string;
  logger?: string;
  message?: LogToken[];
}

const LOG_HEADER =
  /^(?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\[(?<thread>[^\]]*)\]\s+(?<logger>\S+)\s+-\s+(?<message>.*)$/;

// Keep highlighting selective: identifiers such as jobId and arbitrary numbers stay neutral.
const MESSAGE_TOKENS =
  /\b(checkpointId|recordsOut|recordsIn|readRate|readRows|writeRows|duration|durationMs|elapsed|elapsedMs|batchSize|throughput)=([0-9]+(?:\.[0-9]+)?(?:ms|s|m)?)\b|\b((?:[A-Za-z_$][\w$]*\.)*[A-Za-z_$][\w$]*(?:Exception|Error))\b/gi;

export function tokenizeLogMessage(message: string): LogToken[] {
  const tokens: LogToken[] = [];
  let offset = 0;

  for (const match of message.matchAll(MESSAGE_TOKENS)) {
    const start = match.index;
    if (start > offset) tokens.push({ text: message.slice(offset, start), kind: "text" });

    if (match[3]) {
      tokens.push({ text: match[0], kind: "exception" });
    } else {
      tokens.push({ text: match[1] + "=", kind: "key" });
      tokens.push({ text: match[2], kind: "value" });
    }
    offset = start + match[0].length;
  }

  if (offset < message.length) tokens.push({ text: message.slice(offset), kind: "text" });
  return tokens;
}

export function parseLogLine(raw: string): ParsedLogLine {
  const groups = LOG_HEADER.exec(raw)?.groups;
  if (groups) {
    return {
      raw,
      kind: "structured",
      timestamp: groups.timestamp,
      level: groups.level as LogLevel,
      thread: groups.thread,
      logger: groups.logger,
      message: tokenizeLogMessage(groups.message),
    };
  }

  // Continuation lines (including Java stack frames) must not be discarded.
  return { raw, kind: /^\s+at\s+\S+/.test(raw) ? "stack" : "plain" };
}

export function parseLogContent(content: string): ParsedLogLine[] {
  return content ? content.split(/\r?\n/).map(parseLogLine) : [];
}

/** Drop complete oldest lines, never start the viewer in the middle of a log record. */
export function trimLogContent(content: string, maxCharacters = 180000): string {
  if (content.length <= maxCharacters) return content;
  const cutoff = content.length - maxCharacters;
  if (content[cutoff - 1] === "\n") return content.slice(cutoff);
  const breakIndex = content.indexOf("\n", cutoff);
  return breakIndex < 0 ? content.slice(-maxCharacters) : content.slice(breakIndex + 1);
}
