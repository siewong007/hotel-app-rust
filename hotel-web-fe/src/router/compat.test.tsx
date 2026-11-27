import { act, cleanup, render, screen } from '@testing-library/react';
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from '@tanstack/react-router';
import { afterEach, describe, expect, it } from 'vitest';
import {
  Navigate,
  parsePlainSearch,
  stringifyPlainSearch,
  useLocation,
  useNavigate,
  useSearchParams,
} from './compat';

afterEach(cleanup);

describe('router/compat plain query serialization', () => {
  it('round-trips numeric string query values without JSON quoting', () => {
    expect(parsePlainSearch('?create=1&booking_id=802101')).toEqual({
      create: '1',
      booking_id: '802101',
    });
    expect(stringifyPlainSearch({ create: '1', booking_id: 802101, empty: '', omitted: undefined })).toBe(
      '?create=1&booking_id=802101',
    );
  });

  it('keeps ?create=1 readable via useSearchParams after compat useNavigate()', async () => {
    function BookingsProbe() {
      const navigate = useNavigate();
      const [params] = useSearchParams();
      return (
        <div>
          <span data-testid="create-flag">{params.get('create') ?? 'none'}</span>
          <button type="button" onClick={() => navigate('/bookings?create=1')}>
            New booking
          </button>
        </div>
      );
    }

    const rootRoute = createRootRoute({ component: () => <Outlet /> });
    const bookingsRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/bookings',
      component: BookingsProbe,
    });
    const router = createRouter({
      routeTree: rootRoute.addChildren([bookingsRoute]),
      history: createMemoryHistory({ initialEntries: ['/bookings'] }),
      parseSearch: parsePlainSearch,
      stringifySearch: stringifyPlainSearch,
    });

    render(<RouterProvider router={router} />);
    expect((await screen.findByTestId('create-flag')).textContent).toBe('none');

    await act(async () => {
      screen.getByRole('button', { name: 'New booking' }).click();
    });
    expect((await screen.findByTestId('create-flag')).textContent).toBe('1');
  });

  it('splits pathname and query string inside compat <Navigate />', async () => {
    function BookingsTarget() {
      const loc = useLocation();
      return <div data-testid="bookings-loc">{`${loc.pathname}${loc.search}`}</div>;
    }

    const rootRoute = createRootRoute({ component: () => <Outlet /> });
    const sourceRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/redirect-source',
      component: () => <Navigate to="/bookings?view=arriving&booking_id=42" replace />,
    });
    const destRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/bookings',
      component: BookingsTarget,
    });
    const router = createRouter({
      routeTree: rootRoute.addChildren([sourceRoute, destRoute]),
      history: createMemoryHistory({ initialEntries: ['/redirect-source'] }),
      parseSearch: parsePlainSearch,
      stringifySearch: stringifyPlainSearch,
    });

    render(<RouterProvider router={router} />);
    expect((await screen.findByTestId('bookings-loc')).textContent).toBe(
      '/bookings?view=arriving&booking_id=42',
    );
  });
});
