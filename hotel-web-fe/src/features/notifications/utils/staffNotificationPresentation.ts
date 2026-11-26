import type { UseTranslationResult } from '../../../i18n';
import type {
  StaffNotificationCategory,
  StaffNotificationItem,
  StaffNotificationPriority,
} from '../../admin/system/types';

type Translate = UseTranslationResult['t'];

export function resolvePriority(item: StaffNotificationItem): StaffNotificationPriority {
  if (item.priority === 'urgent' || item.priority === 'high' || item.priority === 'normal') {
    return item.priority;
  }
  switch (item.kind) {
    case 'bank_transfer_pending':
    case 'payment_receipt_uploaded':
    case 'job_failure':
    case 'support_sla_breached':
    case 'maintenance_critical':
      return 'urgent';
    case 'guest_support_conversation':
      return item.title.trim().toLowerCase() === 'cancellation request' ? 'urgent' : 'high';
    case 'guest_support_message':
    case 'support_sla_at_risk':
    case 'ekyc_pending_review':
    case 'maintenance_high':
      return 'high';
    default:
      return 'normal';
  }
}

export function resolveCategory(item: StaffNotificationItem): StaffNotificationCategory {
  if (
    item.category === 'payments' ||
    item.category === 'support' ||
    item.category === 'bookings' ||
    item.category === 'ekyc' ||
    item.category === 'maintenance' ||
    item.category === 'housekeeping' ||
    item.category === 'system'
  ) {
    return item.category;
  }
  switch (item.kind) {
    case 'bank_transfer_pending':
    case 'payment_receipt_uploaded':
      return 'payments';
    case 'guest_support_conversation':
    case 'guest_support_message':
    case 'support_sla_breached':
    case 'support_sla_at_risk':
      return 'support';
    case 'online_booking_created':
      return 'bookings';
    case 'ekyc_pending_review':
      return 'ekyc';
    case 'maintenance_critical':
    case 'maintenance_high':
      return 'maintenance';
    default:
      return 'system';
  }
}

export function resolveActionUrl(item: StaffNotificationItem): string | null {
  if (item.action_url) return item.action_url;
  switch (item.kind) {
    case 'bank_transfer_pending':
    case 'payment_receipt_uploaded':
      return '/payment-approvals';
    case 'guest_support_conversation':
      return item.subject
        ? `/support?search=${encodeURIComponent(item.subject)}`
        : '/support';
    case 'guest_support_message': {
      const conv = item.subject?.split(':')[0]?.trim();
      return conv ? `/support?search=${encodeURIComponent(conv)}` : '/support';
    }
    case 'support_sla_breached':
    case 'support_sla_at_risk': {
      const conv = item.subject?.split(':')[0]?.trim();
      return conv
        ? `/support?queue=at_risk&search=${encodeURIComponent(conv)}`
        : '/support?queue=at_risk';
    }
    case 'ekyc_pending_review':
      return item.subject && /^\d+$/.test(item.subject.trim())
        ? `/ekyc-admin?application_id=${item.subject.trim()}`
        : '/ekyc-admin';
    case 'maintenance_critical':
    case 'maintenance_high':
      return '/housekeeping?tab=maintenance';
    case 'online_booking_created':
      return item.subject
        ? `/bookings?search=${encodeURIComponent(item.subject)}`
        : '/bookings';
    case 'job_failure':
      return '/jobs';
    default:
      return null;
  }
}

export function resolveActionPending(item: StaffNotificationItem): boolean {
  return item.action_pending ?? (item.read_at === null);
}

export function resolveRequiresAction(item: StaffNotificationItem): boolean {
  if (typeof item.requires_action === 'boolean') return item.requires_action;
  return item.kind !== 'online_booking_created';
}

export function resolveActionLabel(item: StaffNotificationItem, t: Translate): string {
  const key = item.action_label_key ?? fallbackActionLabelKey(item.kind, item.title);
  switch (key) {
    case 'reviewPayment':
      return t('actions.reviewPayment');
    case 'reviewReceipt':
      return t('actions.reviewReceipt');
    case 'reviewCancellation':
      return t('actions.reviewCancellation');
    case 'replyGuest':
      return t('actions.replyGuest');
    case 'handleSlaBreach':
      return t('actions.handleSlaBreach');
    case 'replyBeforeSla':
      return t('actions.replyBeforeSla');
    case 'reviewEkyc':
      return t('actions.reviewEkyc');
    case 'openMaintenance':
      return t('actions.openMaintenance');
    case 'viewBooking':
      return t('actions.viewBooking');
    case 'inspectJobs':
      return t('actions.inspectJobs');
    default:
      return t('actions.open');
  }
}

function fallbackActionLabelKey(kind: string, title: string): string | null {
  switch (kind) {
    case 'bank_transfer_pending':
      return 'reviewPayment';
    case 'payment_receipt_uploaded':
      return 'reviewReceipt';
    case 'guest_support_conversation':
      return title.trim().toLowerCase() === 'cancellation request'
        ? 'reviewCancellation'
        : 'replyGuest';
    case 'guest_support_message':
      return 'replyGuest';
    case 'support_sla_breached':
      return 'handleSlaBreach';
    case 'support_sla_at_risk':
      return 'replyBeforeSla';
    case 'ekyc_pending_review':
      return 'reviewEkyc';
    case 'maintenance_critical':
    case 'maintenance_high':
      return 'openMaintenance';
    case 'online_booking_created':
      return 'viewBooking';
    case 'job_failure':
      return 'inspectJobs';
    default:
      return null;
  }
}

export function resolveTitleAndBody(
  item: StaffNotificationItem,
  t: Translate,
): { title: string; body: string | null } {
  const subject = item.subject ?? '';
  switch (item.kind) {
    case 'bank_transfer_pending':
      return {
        title: t('center.bankTransferPendingTitle'),
        body: t('center.bankTransferPendingBody', { booking: subject }),
      };
    case 'payment_receipt_uploaded':
      return {
        title: t('templates.paymentReceiptUploadedTitle'),
        body: t('templates.paymentReceiptUploadedBody', { booking: subject }),
      };
    case 'support_sla_breached': {
      const conversation = subject.split(':')[0] ?? subject;
      return {
        title: t('templates.supportSlaBreachedTitle'),
        body: item.body ?? t('templates.supportSlaBreachedBody', { conversation }),
      };
    }
    case 'support_sla_at_risk': {
      const conversation = subject.split(':')[0] ?? subject;
      return {
        title: t('templates.supportSlaAtRiskTitle'),
        body: item.body ?? t('templates.supportSlaAtRiskBody', { conversation }),
      };
    }
    case 'ekyc_pending_review':
      return {
        title: t('templates.ekycPendingTitle'),
        body: item.body ?? t('templates.ekycPendingBody', { id: subject }),
      };
    case 'maintenance_critical':
      return {
        title: t('templates.maintenanceCriticalTitle'),
        body: item.body ?? t('templates.maintenanceBody', { ticket: subject }),
      };
    case 'maintenance_high':
      return {
        title: t('templates.maintenanceHighTitle'),
        body: item.body ?? t('templates.maintenanceBody', { ticket: subject }),
      };
    case 'online_booking_created':
      return {
        title: t('templates.onlineBookingTitle'),
        body: item.body ?? t('templates.onlineBookingBody', { booking: subject }),
      };
    default:
      return {
        title: item.title,
        body: item.body,
      };
  }
}

export function categoryLabel(category: StaffNotificationCategory, t: Translate): string {
  switch (category) {
    case 'payments':
      return t('categories.payments');
    case 'support':
      return t('categories.support');
    case 'bookings':
      return t('categories.bookings');
    case 'ekyc':
      return t('categories.ekyc');
    case 'maintenance':
      return t('categories.maintenance');
    case 'housekeeping':
      return t('categories.housekeeping');
    case 'system':
      return t('categories.system');
  }
}

export function priorityLabel(priority: StaffNotificationPriority, t: Translate): string {
  switch (priority) {
    case 'urgent':
      return t('staffPriority.urgent');
    case 'high':
      return t('staffPriority.high');
    case 'normal':
      return t('staffPriority.normal');
  }
}
