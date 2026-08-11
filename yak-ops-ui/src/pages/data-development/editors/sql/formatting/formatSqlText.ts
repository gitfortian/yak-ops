const protectSqlSegments = (sql: string) => {
  const segments: string[] = [];
  let output = '';
  let index = 0;

  const pushSegment = (value: string) => {
    const token = `__YAK_SQL_SEGMENT_${segments.length}__`;
    segments.push(value);
    output += token;
  };

  while (index < sql.length) {
    const current = sql[index];
    const next = sql[index + 1];

    if (current === "'" || current === '"' || current === '`') {
      const quote = current;
      let end = index + 1;
      while (end < sql.length) {
        if (sql[end] === quote && sql[end + 1] === quote) {
          end += 2;
          continue;
        }
        if (sql[end] === quote) {
          end += 1;
          break;
        }
        end += 1;
      }
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    if (current === '[') {
      let end = index + 1;
      while (end < sql.length) {
        if (sql[end] === ']' && sql[end + 1] === ']') {
          end += 2;
          continue;
        }
        if (sql[end] === ']') {
          end += 1;
          break;
        }
        end += 1;
      }
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    if (current === '-' && next === '-') {
      let end = index + 2;
      while (end < sql.length && sql[end] !== '\n') end += 1;
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    if (current === '/' && next === '*') {
      let end = index + 2;
      while (end < sql.length && !(sql[end] === '*' && sql[end + 1] === '/')) {
        end += 1;
      }
      end = Math.min(sql.length, end + 2);
      pushSegment(sql.slice(index, end));
      index = end;
      continue;
    }

    output += current;
    index += 1;
  }

  return { output, segments };
};

const restoreSqlSegments = (sql: string, segments: string[]) =>
  segments.reduce(
    (value, segment, index) =>
      value.replaceAll(`__YAK_SQL_SEGMENT_${index}__`, segment),
    sql,
  );

const CLAUSE_PATTERN = [
  'LEFT\\s+OUTER\\s+JOIN',
  'RIGHT\\s+OUTER\\s+JOIN',
  'FULL\\s+OUTER\\s+JOIN',
  'LEFT\\s+JOIN',
  'RIGHT\\s+JOIN',
  'FULL\\s+JOIN',
  'INNER\\s+JOIN',
  'CROSS\\s+JOIN',
  'GROUP\\s+BY',
  'ORDER\\s+BY',
  'UNION\\s+ALL',
  'UNION',
  'FROM',
  'WHERE',
  'HAVING',
  'JOIN',
  'SET',
  'VALUES',
  'LIMIT',
  'OFFSET',
].join('|');

export const formatSqlText = (sql: string) => {
  if (!sql.trim()) return sql;

  const normalized = sql.replace(/\r\n?/g, '\n');
  const { output, segments } = protectSqlSegments(normalized);

  let formatted = output
    .replace(/[ \t]+/g, ' ')
    .replace(/ *\n */g, '\n')
    .replace(new RegExp(`\\s+(${CLAUSE_PATTERN})\\s+`, 'gi'), '\n$1 ')
    .replace(/\s+(AND|OR)\s+/gi, '\n  $1 ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();

  formatted = restoreSqlSegments(formatted, segments);
  return formatted;
};
