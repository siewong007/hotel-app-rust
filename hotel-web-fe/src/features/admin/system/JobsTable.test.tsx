import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { JobsTable } from './JobsTable';
import type { JobHealth } from './types';

const job = (job_name: string, failures_24h: number, last_error: string | null = null): JobHealth => ({
  job_name,
  last_status: last_error ? 'error' : 'ok',
  last_run_at: '2026-09-14T02:00:00Z',
  last_duration_ms: 1500,
  last_error,
  last_detail: null,
  runs_24h: 4,
  failures_24h,
});

const firstColumnCodes = () =>
  screen.getAllByRole('row').slice(1).map((row) => row.querySelector('td .MuiTypography-caption')?.textContent);

describe('JobsTable', () => {
  afterEach(cleanup);

  it('renders each job with its duration, failures and last error', () => {
    render(<JobsTable jobs={[job('night_audit', 0), job('payment_receipts', 2, 'SMTP timeout')]} />);
    expect(screen.getByText('night_audit')).toBeTruthy();
    expect(screen.getAllByText('1.5s')).toHaveLength(2);
    expect(screen.getByText('SMTP timeout')).toBeTruthy();
  });

  it('sorts by failures when the header is clicked', () => {
    render(<JobsTable jobs={[job('night_audit', 0), job('payment_receipts', 2, 'x'), job('session_retention', 1, 'y')]} />);
    expect(firstColumnCodes()).toEqual(['night_audit', 'payment_receipts', 'session_retention']);
    fireEvent.click(screen.getByRole('button', { name: /failures/i }));
    const ascending = firstColumnCodes();
    fireEvent.click(screen.getByRole('button', { name: /failures/i }));
    const descending = firstColumnCodes();
    expect([ascending, descending]).toContainEqual(['payment_receipts', 'session_retention', 'night_audit']);
  });
});
