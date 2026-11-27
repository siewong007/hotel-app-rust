import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryStaleTime } from '../../../api/queryConfig';
import { queryKeys } from '../../../api/queryKeys';
import {
  EkycActionPayload,
  EkycAdminCreatePayload,
  EkycListParams,
  EkycService,
} from '../../../api/ekyc.service';

export function useEkycStatus() {
  return useQuery({
    queryKey: queryKeys.ekyc.myStatus(),
    queryFn: () => EkycService.getEkycStatus(),
    staleTime: queryStaleTime.standard,
  });
}

export function useAllEkycVerifications(params?: EkycListParams) {
  return useQuery({
    queryKey: queryKeys.ekyc.allVerifications(params),
    queryFn: () => EkycService.getAllEkycVerifications(params),
    staleTime: queryStaleTime.short,
  });
}

export function useEkycApplication(applicationId?: number) {
  return useQuery({
    queryKey: applicationId ? queryKeys.ekyc.application(applicationId) : queryKeys.ekyc.application('none'),
    queryFn: () => EkycService.getEkycApplication(applicationId as number),
    enabled: Boolean(applicationId),
    staleTime: queryStaleTime.short,
  });
}

export function useEkycReasonCodes(enabled = true) {
  return useQuery({
    queryKey: queryKeys.ekyc.reasonCodes(),
    queryFn: () => EkycService.getReasonCodes(),
    enabled,
    staleTime: queryStaleTime.standard,
  });
}

export function useSubmitEkycVerification() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: Parameters<typeof EkycService.submitEkycVerification>[0]) =>
      EkycService.submitEkycVerification(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.ekyc.all });
    },
  });
}

export function useCreateEkycApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: EkycAdminCreatePayload) => EkycService.createEkycApplication(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.ekyc.all });
    },
  });
}

export function useReviewEkycAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ applicationId, payload }: { applicationId: number; payload: EkycActionPayload }) =>
      EkycService.performReviewAction(applicationId, payload),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: queryKeys.ekyc.all });
      qc.invalidateQueries({ queryKey: queryKeys.ekyc.application(variables.applicationId) });
    },
  });
}

export function useRevealEkycField() {
  return useMutation({
    mutationFn: ({ applicationId, field, reason }: { applicationId: number; field: string; reason: string }) =>
      EkycService.revealSensitiveField(applicationId, field, reason),
  });
}

export function useApproveEkyc() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: number | { verificationId: number; expectedVersion?: number }) =>
      typeof input === 'number'
        ? EkycService.approveEkycVerification(input)
        : EkycService.approveEkycVerification(input.verificationId, input.expectedVersion),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.ekyc.all });
    },
  });
}

export function useRejectEkyc() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      verificationId,
      reason,
      expectedVersion,
    }: {
      verificationId: number;
      reason: string;
      expectedVersion?: number;
    }) => EkycService.rejectEkycVerification(verificationId, reason, expectedVersion),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.ekyc.all });
    },
  });
}
