import { afterEach, describe, expect, it, vi } from 'vitest';
import { reportClientError, scrubPiiFromErrorText, type SanitizedClientErrorReport } from './errorReporter';

describe('errorReporter', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('scrubs emails, JWT/bearer/hex booking tokens, query secrets, and Malaysian NRIC numbers', () => {
    const raw =
      'Failed for guest@example.com with Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig ' +
      'at /guest-portal?token=secret123&code=otp999 ' +
      'hash 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef ' +
      'NRIC 900101-13-5555 or 900101135555';

    const scrubbed = scrubPiiFromErrorText(raw);

    expect(scrubbed).not.toContain('guest@example.com');
    expect(scrubbed).not.toContain('eyJhbGciOiJIUzI1NiJ9');
    expect(scrubbed).not.toContain('secret123');
    expect(scrubbed).not.toContain('otp999');
    expect(scrubbed).not.toContain('0123456789abcdef0123456789abcdef');
    expect(scrubbed).not.toContain('900101-13-5555');
    expect(scrubbed).not.toContain('900101135555');
    expect(scrubbed).toContain('[redacted-email]');
    expect(scrubbed).toContain('Bearer [redacted-token]');
    expect(scrubbed).toContain('?token=[redacted]&code=[redacted]');
    expect(scrubbed).toContain('[redacted-token]');
    expect(scrubbed).toContain('[redacted-id]');
  });

  it('dispatches a sanitized app:client-error event with scrubbed context', () => {
    const received: SanitizedClientErrorReport[] = [];
    const listener = (event: Event) => {
      received.push((event as CustomEvent<SanitizedClientErrorReport>).detail);
    };
    window.addEventListener('app:client-error', listener);

    const report = reportClientError(new Error('Email alice@saliminn.my failed for 880505-13-1234'), {
      boundary: 'page',
      componentStack: 'at GuestView (token=abc)',
    });

    window.removeEventListener('app:client-error', listener);

    expect(report.name).toBe('Error');
    expect(report.boundary).toBe('page');
    expect(report.message).toBe('Email [redacted-email] failed for [redacted-id]');
    expect(received).toHaveLength(1);
    expect(received[0]).toEqual(report);
  });
});
