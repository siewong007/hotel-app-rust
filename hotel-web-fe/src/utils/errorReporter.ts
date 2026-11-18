export interface ClientErrorContext {
  boundary?: string;
  componentStack?: string | null;
}

export interface SanitizedClientErrorReport {
  name: string;
  message: string;
  boundary?: string;
  componentStack?: string;
  timestamp: string;
}

const EMAIL_PATTERN = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
const BEARER_PATTERN = /Bearer\s+[A-Za-z0-9._-]+/gi;
const JWT_PATTERN = /\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9._-]{8,}(?:\.[A-Za-z0-9._-]+)?\b/g;
const HEX_TOKEN_PATTERN = /\b[0-9a-f]{64}\b/gi;
const QUERY_TOKEN_PATTERN = /([?&](?:token|code|access_token)=)[^&\s#]+/gi;
const NRIC_HYPHEN_PATTERN = /\b\d{6}-\d{2}-\d{4}\b/g;
const LONG_DIGIT_ID_PATTERN = /\b(?:\d[ -]?){11,18}\d\b/g;

/** Strip emails, bearer/JWT/booking tokens, query secrets, and IC/card numbers from client error text. */
export function scrubPiiFromErrorText(input: string): string {
  return input
    .replace(QUERY_TOKEN_PATTERN, '$1[redacted]')
    .replace(BEARER_PATTERN, 'Bearer [redacted-token]')
    .replace(JWT_PATTERN, '[redacted-token]')
    .replace(HEX_TOKEN_PATTERN, '[redacted-token]')
    .replace(EMAIL_PATTERN, '[redacted-email]')
    .replace(NRIC_HYPHEN_PATTERN, '[redacted-id]')
    .replace(LONG_DIGIT_ID_PATTERN, '[redacted-id]');
}

/**
 * Emit a PII-scrubbed `app:client-error` DOM event so external telemetry hooks
 * can subscribe centrally without leaking guest emails, NRICs, or access tokens.
 */
export function reportClientError(
  error: unknown,
  context?: ClientErrorContext,
): SanitizedClientErrorReport {
  const name = error instanceof Error ? error.name : 'UnknownError';
  const rawMessage = error instanceof Error ? error.message : String(error ?? '');
  const report: SanitizedClientErrorReport = {
    name,
    message: scrubPiiFromErrorText(rawMessage),
    ...(context?.boundary ? { boundary: context.boundary } : {}),
    ...(context?.componentStack
      ? { componentStack: scrubPiiFromErrorText(context.componentStack) }
      : {}),
    timestamp: new Date().toISOString(),
  };

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent<SanitizedClientErrorReport>('app:client-error', { detail: report }));
  }

  return report;
}
