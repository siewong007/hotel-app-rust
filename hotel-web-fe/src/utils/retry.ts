/**
 * Retry utility for API calls with exponential backoff
 */

import { isHTTPError } from 'ky';
import { APIError } from '../api/client';
import { t } from '../i18n';

export interface RetryOptions {
  maxAttempts?: number;
  initialDelay?: number;
  maxDelay?: number;
  backoffFactor?: number;
  shouldRetry?: (error: Error, attempt: number) => boolean;
  onRetry?: (error: Error, attempt: number, delay: number) => void;
}

function extractStatusCode(error: Error): number | undefined {
  if (isHTTPError(error)) {
    return error.response.status;
  }
  if (error instanceof APIError && typeof error.statusCode === 'number') {
    return error.statusCode;
  }
  const match = error.message.match(/(?:\bstatus(?:\s+code)?\s+|^)(\d{3})\b/i);
  return match ? Number(match[1]) : undefined;
}

function isRetryableStatus(status: number): boolean {
  return status === 408 || status === 429 || status >= 500;
}

const DEFAULT_OPTIONS: Required<RetryOptions> = {
  maxAttempts: 3,
  initialDelay: 1000,
  maxDelay: 10000,
  backoffFactor: 2,
  shouldRetry: (error: Error) => {
    const status = extractStatusCode(error);
    if (status !== undefined) {
      return isRetryableStatus(status);
    }
    if (
      error instanceof TypeError ||
      error.message.includes('NetworkError') ||
      error.message.includes('Failed to fetch')
    ) {
      return true;
    }
    return false;
  },
  onRetry: (error: Error, attempt: number, delay: number) => {
    console.warn(`Retry attempt ${attempt} after ${delay}ms due to:`, error.message);
  },
};

/**
 * Execute a function with retry logic and exponential backoff
 */
export async function withRetry<T>(
  fn: () => Promise<T>,
  options: RetryOptions = {}
): Promise<T> {
  const opts = { ...DEFAULT_OPTIONS, ...options };
  let lastError: Error | null = null;

  for (let attempt = 1; attempt <= opts.maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error as Error;

      // Check if we should retry
      if (attempt >= opts.maxAttempts || !opts.shouldRetry(lastError, attempt)) {
        throw lastError;
      }

      // Calculate delay with exponential backoff
      const delay = Math.min(
        opts.initialDelay * Math.pow(opts.backoffFactor, attempt - 1),
        opts.maxDelay
      );

      // Call retry callback
      opts.onRetry(lastError, attempt, delay);

      // Wait before retrying
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }

  throw lastError || new Error(t('errors:request.retryExhausted'));
}

/**
 * Create a retryable version of an async function
 */
export function retryable<TArgs extends any[], TReturn>(
  fn: (...args: TArgs) => Promise<TReturn>,
  options: RetryOptions = {}
): (...args: TArgs) => Promise<TReturn> {
  return (...args: TArgs) => withRetry(() => fn(...args), options);
}

/**
 * Batch multiple requests with retry logic
 */
export async function batchWithRetry<T>(
  requests: Array<() => Promise<T>>,
  options: RetryOptions & { concurrency?: number } = {}
): Promise<T[]> {
  const { concurrency = 5, ...retryOptions } = options;
  const results: T[] = [];
  const queue = [...requests];

  while (queue.length > 0) {
    const batch = queue.splice(0, concurrency);
    const batchResults = await Promise.all(
      batch.map(request => withRetry(request, retryOptions))
    );
    results.push(...batchResults);
  }

  return results;
}

/**
 * Retry wrapper for React Query / SWR style hooks
 */
export function createRetryConfig(options: RetryOptions = {}) {
  return {
    retry: options.maxAttempts || DEFAULT_OPTIONS.maxAttempts,
    retryDelay: (attempt: number) => {
      const delay = Math.min(
        (options.initialDelay || DEFAULT_OPTIONS.initialDelay) *
        Math.pow(options.backoffFactor || DEFAULT_OPTIONS.backoffFactor, attempt - 1),
        options.maxDelay || DEFAULT_OPTIONS.maxDelay
      );
      return delay;
    },
  };
}
