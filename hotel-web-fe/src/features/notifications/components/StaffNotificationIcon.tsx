import React from 'react';
import BuildIcon from '@mui/icons-material/Build';
import CleaningServicesIcon from '@mui/icons-material/CleaningServices';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutlined';
import EventNoteIcon from '@mui/icons-material/EventNote';
import PaymentsIcon from '@mui/icons-material/Payments';
import SupportAgentIcon from '@mui/icons-material/SupportAgent';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import type {
  StaffNotificationCategory,
  StaffNotificationPriority,
} from '../../admin/system/types';

export function priorityAccentColor(
  priority: StaffNotificationPriority,
  pending: boolean,
): string {
  if (!pending) return 'text.disabled';
  switch (priority) {
    case 'urgent':
      return 'error.main';
    case 'high':
      return 'warning.main';
    default:
      return 'info.main';
  }
}

export const StaffNotificationIcon: React.FC<{
  category: StaffNotificationCategory;
  priority: StaffNotificationPriority;
  pending: boolean;
}> = ({ category, priority, pending }) => {
  const color = priorityAccentColor(priority, pending);
  const sx = { color, fontSize: 18 };
  switch (category) {
    case 'payments':
      return <PaymentsIcon sx={sx} />;
    case 'support':
      return <SupportAgentIcon sx={sx} />;
    case 'bookings':
      return <EventNoteIcon sx={sx} />;
    case 'ekyc':
      return <VerifiedUserIcon sx={sx} />;
    case 'maintenance':
      return <BuildIcon sx={sx} />;
    case 'housekeeping':
      return <CleaningServicesIcon sx={sx} />;
    case 'system':
      return <ErrorOutlineIcon sx={sx} />;
  }
};
