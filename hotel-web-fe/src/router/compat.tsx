/**
 * react-router-dom compatibility layer on top of @tanstack/react-router.
 *
 * Existing call sites use the function-style `navigate(path)` and `[searchParams]`
 * pair from react-router-dom. These shims translate those to TanStack Router's
 * object-style APIs so consumer files only need to change their import path.
 */
import React from 'react';
import {
  Link as TanstackLink,
  Navigate as TanstackNavigate,
  useNavigate as useTanstackNavigate,
  useLocation as useTanstackLocation,
  useRouter,
} from '@tanstack/react-router';
import { hrefFromAppPath, shouldUseDocumentNavigation } from '../guest/guestDocumentPaths';

type NavigateInput = string | number;
interface CompatNavigateOptions {
  replace?: boolean;
  state?: unknown;
}

export interface CompatNavigateFn {
  (to: NavigateInput, options?: CompatNavigateOptions): void;
}

/**
 * Returns a navigate function compatible with react-router-dom v6/v7:
 *   navigate('/foo')
 *   navigate('/foo?x=1')
 *   navigate('/foo', { replace: true })
 *   navigate(-1)   // history.back()
 */
export function useNavigate(): CompatNavigateFn {
  const tsNavigate = useTanstackNavigate();
  const router = useRouter();
  return React.useCallback(
    (to: NavigateInput, options?: CompatNavigateOptions) => {
      if (typeof to === 'number') {
        router.history.go(to);
        return;
      }

      // TanStack Router expects the route pathname and search object
      // separately. Passing `/login?account=guest` as `to` makes it look for a
      // literal route with a question mark, which falls through to the index
      // page instead of opening the intended screen.
      const target = new URL(to, window.location.origin);
      if (shouldUseDocumentNavigation(target.pathname, window.location.pathname)) {
        const href = hrefFromAppPath(to);
        if (options?.replace) {
          window.location.replace(href);
        } else {
          window.location.assign(href);
        }
        return;
      }
      tsNavigate({
        // Dynamic URL strings accepted by the compat shim cannot satisfy TanStack's static route union.
        to: target.pathname as any,
        // Query parameters parsed from arbitrary URLs have dynamic key/value shapes.
        search: Object.fromEntries(target.searchParams.entries()) as any,
        hash: target.hash.replace(/^#/, ''),
        replace: options?.replace,
      });
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('hotel:locationchange'));
      }
    },
    [tsNavigate, router]
  );
}

/**
 * Reads `window.location.search` without requiring a TanStack Router provider,
 * staying synced on popstate and compat `navigate()` transitions.
 */
export function useBrowserSearchParams(): URLSearchParams {
  const [search, setSearch] = React.useState(() =>
    typeof window !== 'undefined' ? window.location.search : '',
  );

  React.useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const sync = () => setSearch(window.location.search);
    window.addEventListener('popstate', sync);
    window.addEventListener('hotel:locationchange', sync);
    return () => {
      window.removeEventListener('popstate', sync);
      window.removeEventListener('hotel:locationchange', sync);
    };
  }, []);

  return React.useMemo(() => new URLSearchParams(search), [search]);
}

export interface CompatLocation {
  pathname: string;
  search: string;
  hash: string;
  state: unknown;
  key: string;
}

export function useLocation(): CompatLocation {
  const loc = useTanstackLocation();
  return {
    pathname: loc.pathname,
    search: loc.searchStr ? `?${loc.searchStr.replace(/^\?+/, '')}` : '',
    hash: loc.hash ?? '',
    state: loc.state,
    key: (loc.state as { key?: string } | undefined)?.key ?? 'default',
  };
}

type SetURLSearchParamsInit = URLSearchParams | string | Record<string, string> | undefined;
interface SetURLSearchParamsOptions {
  replace?: boolean;
}

export function useSearchParams(): [URLSearchParams, (next: SetURLSearchParamsInit, options?: SetURLSearchParamsOptions) => void] {
  const loc = useTanstackLocation();
  const tsNavigate = useTanstackNavigate();

  const params = React.useMemo(() => {
    const raw = loc.searchStr ?? '';
    return new URLSearchParams(raw.replace(/^\?+/, ''));
  }, [loc.searchStr]);

  const setSearchParams = React.useCallback(
    (next: SetURLSearchParamsInit, options?: SetURLSearchParamsOptions) => {
      let nextParams: URLSearchParams;
      if (next instanceof URLSearchParams) {
        nextParams = next;
      } else if (typeof next === 'string') {
        nextParams = new URLSearchParams(next.replace(/^\?+/, ''));
      } else if (next && typeof next === 'object') {
        nextParams = new URLSearchParams(next);
      } else {
        nextParams = new URLSearchParams();
      }
      const search = nextParams.toString();
      tsNavigate({
        to: loc.pathname,
        search: () => Object.fromEntries(nextParams.entries()),
        replace: options?.replace,
        // Cast required because loc.pathname is a runtime string outside TanStack's generated route map.
      } as any);
      // Fallback for browsers/queries that prefer raw string (rarely hit since tsNavigate handles it).
      void search;
    },
    [tsNavigate, loc.pathname]
  );

  return [params, setSearchParams];
}

/**
 * Drop-in Link with the same props shape react-router-dom uses (string `to`).
 * TanStack Router's Link can accept a string for unmapped paths via the `as any` cast.
 */
export interface CompatLinkProps extends Omit<React.ComponentProps<typeof TanstackLink>, 'to'> {
  to: string;
  replace?: boolean;
}

export const Link = React.forwardRef<HTMLAnchorElement, CompatLinkProps>(function Link(
  { to, replace, ...rest },
  ref
) {
  return (
    <TanstackLink
      // TanStack Link's polymorphic ref type does not accept a plain HTMLAnchorElement ref without widening.
      ref={ref as any}
      // String paths work at runtime even though the generic types prefer typed routes.
      to={to as any}
      replace={replace}
      {...rest}
    />
  );
});

export interface CompatNavigateProps {
  to: string;
  replace?: boolean;
}

export const Navigate: React.FC<CompatNavigateProps> = ({ to, replace }) => {
  const target = new URL(to, window.location.origin);
  if (shouldUseDocumentNavigation(target.pathname, window.location.pathname)) {
    const href = hrefFromAppPath(to);
    if (replace) {
      window.location.replace(href);
    } else {
      window.location.assign(href);
    }
    return null;
  }
  // Dynamic string destinations outside TanStack's static route union require widening.
  return <TanstackNavigate to={to as any} replace={replace} />;
};
