import { memo } from "react";

import type { LogToken, ParsedLogLine } from "./task-instance-log-parser";

interface Props {
  line: ParsedLogLine;
}

function LogMessageToken({ token }: { token: LogToken }) {
  if (token.kind === "text") return <>{token.text}</>;
  return <span className={"yak-log-" + token.kind}>{token.text}</span>;
}

export const TaskInstanceLogLine = memo(function TaskInstanceLogLine({ line }: Props) {
  if (line.kind !== "structured") {
    return (
      <span className={"yak-log-line" + (line.kind === "stack" ? " yak-log-stack" : "")}>
        {line.raw || "\u00a0"}
      </span>
    );
  }

  const logger = line.logger ?? "";
  const lastDot = logger.lastIndexOf(".");

  return (
    <span className="yak-log-line">
      <span className="yak-log-timestamp">{line.timestamp}</span>{" "}
      <span className="yak-log-level" data-level={line.level?.toLowerCase()}>
        {line.level}
      </span>{" "}
      <span className="yak-log-thread">[{line.thread}]</span>{" "}
      {lastDot >= 0 ? (
        <span className="yak-log-namespace">{logger.slice(0, lastDot + 1)}</span>
      ) : null}
      <span className="yak-log-logger">{logger.slice(lastDot + 1)}</span>
      {" - "}
      {line.message?.map((token, index) => (
        <LogMessageToken key={index} token={token} />
      ))}
    </span>
  );
});
