const FORMULA_TRIGGER_RE = /^(?:[=+\-@|\t\r\n]|[\s\t\r\n]+[=+\-@|])/;

/**
 * Neutralizes spreadsheet formula triggers (`=`, `+`, `-`, `@`, `|`, tabs,
 * newlines, or leading whitespace before a formula trigger) and wraps every cell
 * in RFC 4180 quotes.
 */
export function csvCell(value: unknown): string {
  const raw = value === null || value === undefined ? '' : String(value);
  const safe = FORMULA_TRIGGER_RE.test(raw) ? `'${raw}` : raw;
  return `"${safe.replace(/"/g, '""')}"`;
}
