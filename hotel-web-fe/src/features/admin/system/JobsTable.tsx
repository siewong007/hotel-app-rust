import React from 'react';
import { Chip, Tooltip, Typography } from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';

import { useTranslation, statusLabel } from '../../../i18n';
import { formatRelativeTime } from '../../../i18n/format';
import { formatHotelDateTime } from '../../../utils/date';
import { MobileCardRow } from '../../../components/data-table/MobileCardRow';
import { DataTable, type ColumnDef } from '../../../components';
import type { JobHealth } from './types';

function formatDuration(ms: number | null): string {
  if (ms === null) return '—';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

const JOB_LABEL_KEYS: Record<string, string> = {
  night_audit: 'admin:jobs.names.night_audit',
  payment_receipts: 'admin:jobs.names.payment_receipts',
  unpaid_hold_release: 'admin:jobs.names.unpaid_hold_release',
  email_delivery_worker: 'admin:jobs.names.email_delivery_worker',
  email_campaigns: 'admin:jobs.names.email_campaigns',
  birthday_vouchers: 'admin:jobs.names.birthday_vouchers',
  pre_arrival_reminders: 'admin:jobs.names.pre_arrival_reminders',
  session_retention: 'admin:jobs.names.session_retention',
  room_arrival_holds: 'admin:jobs.names.room_arrival_holds',
};

const StatusChip: React.FC<{ status: string; label: string }> = ({ status, label }) => (
  <Chip
    size="small"
    icon={status === 'ok' ? <CheckCircleOutlineIcon /> : <ErrorOutlineIcon />}
    label={label}
    color={status === 'ok' ? 'success' : 'error'}
    variant="outlined"
  />
);

export const JobsTable: React.FC<{ jobs: JobHealth[] }> = ({ jobs }) => {
  const { t } = useTranslation('admin');
  const jobName = (name: string) =>
    JOB_LABEL_KEYS[name] ? t(JOB_LABEL_KEYS[name]) : name;

  const columns: ColumnDef<JobHealth, any>[] = [
    {
      id: 'job',
      accessorFn: (job) => jobName(job.job_name),
      header: t('jobs.col.job'),
      cell: ({ row }) => (
        <>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {jobName(row.original.job_name)}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {row.original.job_name}
          </Typography>
        </>
      ),
    },
    {
      id: 'lastRun',
      accessorKey: 'last_run_at',
      header: t('jobs.col.lastRun'),
      cell: ({ row }) => (
        <Tooltip title={formatHotelDateTime(row.original.last_run_at)}>
          <span>{formatRelativeTime(row.original.last_run_at)}</span>
        </Tooltip>
      ),
    },
    {
      id: 'status',
      accessorKey: 'last_status',
      header: t('common:field.status'),
      cell: ({ row }) => (
        <StatusChip status={row.original.last_status} label={statusLabel(t, 'job', row.original.last_status)} />
      ),
    },
    {
      id: 'duration',
      accessorFn: (job) => job.last_duration_ms ?? -1,
      header: t('jobs.col.duration'),
      meta: { align: 'right' },
      cell: ({ row }) => formatDuration(row.original.last_duration_ms),
    },
    {
      id: 'runs24h',
      accessorKey: 'runs_24h',
      header: t('jobs.col.runs24h'),
      meta: { align: 'right' },
    },
    {
      id: 'failures24h',
      accessorKey: 'failures_24h',
      header: t('jobs.col.failures24h'),
      meta: { align: 'right' },
      cell: ({ row }) =>
        row.original.failures_24h > 0 ? (
          <Typography component="span" color="error.main" sx={{ fontWeight: 700 }}>
            {row.original.failures_24h}
          </Typography>
        ) : (
          0
        ),
    },
    {
      id: 'lastError',
      accessorFn: (job) => job.last_error ?? '',
      header: t('jobs.col.lastError'),
      enableSorting: false,
      cell: ({ row }) =>
        row.original.last_error ? (
          <Tooltip title={row.original.last_error}>
            <Typography
              variant="caption"
              color="error.main"
              sx={{ display: 'block', maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
            >
              {row.original.last_error}
            </Typography>
          </Tooltip>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <DataTable<JobHealth>
      data={jobs}
      columns={columns}
      getRowId={(job) => job.job_name}
      // Sits inside the page's outlined Card: no second Paper border.
      containerProps={{ sx: { borderRadius: 0, boxShadow: 'none', bgcolor: 'transparent' } }}
      renderMobileCard={(job) => (
        <MobileCardRow
          title={jobName(job.job_name)}
          subtitle={t('jobs.cardSubtitle', {
            at: formatRelativeTime(job.last_run_at),
            runs: job.runs_24h,
            failures: job.failures_24h,
          })}
          meta={job.last_error ? t('jobs.lastError', { error: job.last_error }) : t('jobs.lastRunDuration', { duration: formatDuration(job.last_duration_ms) })}
          status={<StatusChip status={job.last_status} label={statusLabel(t, 'job', job.last_status)} />}
        />
      )}
    />
  );
};
