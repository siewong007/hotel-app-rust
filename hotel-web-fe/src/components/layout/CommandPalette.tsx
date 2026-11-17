import React from 'react';
import {
  Box,
  Typography,
  Popover,
  InputBase,
  CircularProgress,
} from '@mui/material';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import AddIcon from '@mui/icons-material/Add';
import AddTaskIcon from '@mui/icons-material/AddTask';
import BookOnlineOutlinedIcon from '@mui/icons-material/BookOnlineOutlined';
import BuildIcon from '@mui/icons-material/Build';
import CleaningServicesOutlinedIcon from '@mui/icons-material/CleaningServicesOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import FlightLandIcon from '@mui/icons-material/FlightLand';
import FlightTakeoffIcon from '@mui/icons-material/FlightTakeoff';
import HelpOutlineIcon from '@mui/icons-material/HelpOutlined';
import HistoryIcon from '@mui/icons-material/History';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import MeetingRoomOutlinedIcon from '@mui/icons-material/MeetingRoomOutlined';
import PaymentsIcon from '@mui/icons-material/Payments';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import PersonOutlineOutlinedIcon from '@mui/icons-material/PersonOutlineOutlined';
import SearchIcon from '@mui/icons-material/Search';
import { useNavigate } from '../../router';
import { ThemeModeContext } from '../../router/ThemeModeContext';
import { useAuth } from '../../auth/AuthContext';
import { useTranslation } from '../../i18n';
import { storage } from '../../utils/storage';
import { useGlobalSearch } from '../../hooks/useGlobalSearch';
import { searchArticles } from '../../features/help/utils';
import { useHelpArticles } from '../../features/help/hooks/useHelpArticles';
import {
  canAccessNavigationRoute,
  navigationRouteDefinitions,
} from '../../navigation/routeRegistry';
import { useRouteLabels } from '../../navigation/routeLabels';

const PALETTE_SCOPES = ['all', 'bookings', 'guests', 'ledgers', 'rooms', 'pages', 'help'] as const;
type PaletteScope = (typeof PALETTE_SCOPES)[number];

const PaletteContext = React.createContext<{ open: () => void } | null>(null);

export const useCommandPalette = () => {
  const ctx = React.useContext(PaletteContext);
  if (!ctx) throw new Error('useCommandPalette outside provider');
  return ctx;
};

// Owns the ⌘K command palette previously embedded in the top navigation: state,
// recents, federated search and the Popover itself. Mounted once in RootLayout
// (inside the staff shell) so any trigger can open it via useCommandPalette().
export const CommandPaletteProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate();
  const themeCtx = React.useContext(ThemeModeContext);
  const { hasPermission, hasRole, getRoutePolicy, user } = useAuth();
  const isGuest = hasRole('guest') || user?.user_type === 'guest';
  const { navLabel: navLabelFor } = useRouteLabels();
  const { t: tNav, tOr } = useTranslation('nav');
  const helpArticles = useHelpArticles();
  const visibleItems = React.useMemo(
    () =>
      navigationRouteDefinitions.filter((item) =>
        canAccessNavigationRoute(item, { hasPermission, hasRole, getRoutePolicy })
      ),
    [hasPermission, hasRole, getRoutePolicy]
  );

  const renderNavIcon = React.useCallback(
    (item: (typeof visibleItems)[number], size: number) => {
      const Icon = item.icon;
      return Icon ? <Icon sx={{ fontSize: size }} /> : null;
    },
    []
  );

  /* ---------------- Command palette ---------------- */
  const cmdInputRef = React.useRef<HTMLInputElement | null>(null);
  const listboxRef = React.useRef<HTMLDivElement | null>(null);
  const [cmdOpen, setCmdOpen] = React.useState(false);
  const [cmdQuery, setCmdQuery] = React.useState('');
  const [scope, setScope] = React.useState<PaletteScope>('all');
  const [activeIndex, setActiveIndex] = React.useState(0);

  // The palette opens from any trigger now, so it anchors to the viewport
  // centre (the old code anchored to the topbar search box element). Tracked in
  // state so the Popover's own resize handling keeps it centred.
  const [viewportCenterX, setViewportCenterX] = React.useState(() => window.innerWidth / 2);
  React.useEffect(() => {
    const onResize = () => setViewportCenterX(window.innerWidth / 2);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  type Recent = { title: string; subtitle?: string; route: string; kind: string };
  const [recents, setRecents] = React.useState<Recent[]>(
    () => (isGuest ? [] : storage.getItem<Recent[]>('cmdRecents') || [])
  );
  const visibleRecents = React.useMemo(() => (isGuest ? [] : recents), [isGuest, recents]);

  React.useEffect(() => {
    if (!isGuest) return;
    storage.removeItem('cmdRecents');
  }, [isGuest]);

  const term = cmdQuery.trim();
  const slash = term.startsWith('/');
  const lowTerm = (slash ? term.slice(1) : term).toLowerCase();

  // Server-side federated search (skipped for /commands or the client-only scopes)
  const serverEnabled = !isGuest && cmdOpen && !slash && scope !== 'pages' && scope !== 'help';
  const serverTypes =
    scope === 'bookings' || scope === 'guests' || scope === 'ledgers' || scope === 'rooms' ? [scope] : undefined;
  const { groups: serverGroups, loading: serverLoading } = useGlobalSearch(
    serverEnabled ? cmdQuery : '',
    { types: serverTypes, enabled: serverEnabled }
  );

  type SelectItem = {
    key: string;
    title: string;
    subtitle?: string;
    icon: React.ReactNode;
    route?: string;
    action?: () => void;
  };
  type Group = { key: string; label: string; items: SelectItem[] };

  const dot = React.useMemo(
    () => <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: 'text.disabled' }} />,
    []
  );

  const serverGroupIcon = React.useCallback(
    (type: string) => {
      switch (type) {
        case 'bookings':
          return <BookOnlineOutlinedIcon sx={{ fontSize: 16 }} />;
        case 'guests':
          return <PersonOutlineOutlinedIcon sx={{ fontSize: 16 }} />;
        case 'ledgers':
          return <AccountBalanceWalletOutlinedIcon sx={{ fontSize: 16 }} />;
        case 'rooms':
          return <MeetingRoomOutlinedIcon sx={{ fontSize: 16 }} />;
        default:
          return dot;
      }
    },
    [dot]
  );

  const persistRecent = (r: Recent) => {
    const next = [r, ...recents.filter((x) => !(x.route === r.route && x.title === r.title))].slice(0, 6);
    setRecents(next);
    storage.setItem('cmdRecents', next);
  };

  const bookingSearchValueFromTitle = (title: string) => {
    const trimmed = title.trim();
    if (/^BK-[A-Za-z0-9-]+$/i.test(trimmed)) return trimmed;
    const numericId = trimmed.match(/^#(\d+)$/);
    return numericId?.[1] || null;
  };

  const routeForSelection = (item: SelectItem): string | undefined => {
    if (!item.route) return undefined;
    const [path, rawSearch = ''] = item.route.split('?');
    const title = item.title.trim();

    if (path === '/room-management' && !rawSearch) {
      const roomMatch = title.match(/^Room\s+(.+)$/i);
      if (roomMatch) {
        return `/room-management?search=${encodeURIComponent(roomMatch[1].trim())}`;
      }
    }

    const ledgerIdTitle = title.match(/^Ledger #(\d+)$/i);
    const searchValue = item.route.startsWith('/bookings')
      ? bookingSearchValueFromTitle(title)
      : item.route.startsWith('/company-ledger')
        ? (ledgerIdTitle?.[1] || title)
        : null;
    if (!searchValue) return item.route;

    const params = new URLSearchParams(rawSearch);
    const existingSearch = params.get('search');
    params.set('search', searchValue);
    if (path === '/bookings' && existingSearch !== searchValue) params.delete('booking_id');
    return `${path}?${params.toString()}`;
  };

  const openCmd = React.useCallback(() => {
    setCmdOpen(true);
    setActiveIndex(0);
    window.setTimeout(() => cmdInputRef.current?.focus(), 0);
  }, []);
  const closeCmd = () => {
    setCmdOpen(false);
    setCmdQuery('');
    setScope('all');
    setActiveIndex(0);
  };

  const groups: Group[] = React.useMemo(() => {
    const showClient = scope === 'all' || scope === 'pages';
    const out: Group[] = [];

    if (!isGuest && !term && visibleRecents.length > 0 && scope === 'all') {
      out.push({
        key: 'recents',
        label: tNav('palette.groups.recent'),
        items: visibleRecents.map((r, i) => ({
          key: `recent-${i}`,
          title: r.title,
          subtitle: r.subtitle,
          icon: <HistoryIcon sx={{ fontSize: 16 }} />,
          route: r.route,
        })),
      });
    }

    if (!isGuest && (scope === 'all' || slash)) {
      const canOpen = (routeId: string) => visibleItems.some((item) => item.id === routeId);
      const matchesAction = (title: string, subtitle: string | undefined, keywords: string[]) =>
        !lowTerm ||
        title.toLowerCase().includes(lowTerm) ||
        (subtitle?.toLowerCase().includes(lowTerm) ?? false) ||
        keywords.some((kw) => kw.includes(lowTerm));

      const actionItems: SelectItem[] = [];
      if (canOpen('bookings')) {
        const newBookingTitle = tNav('mobile.newBooking');
        const newBookingSubtitle = tNav('palette.newBookingHint');
        if (
          !lowTerm ||
          'new booking'.includes(lowTerm) ||
          matchesAction(newBookingTitle, newBookingSubtitle, ['booking', 'create', 'reserve'])
        ) {
          actionItems.push({
            key: 'act-new-booking',
            title: newBookingTitle,
            subtitle: newBookingSubtitle,
            icon: <AddIcon sx={{ fontSize: 16 }} />,
            route: '/bookings?create=1',
          });
        }

        const arrivalsTitle = tNav('mobile.todaysArrivals');
        const arrivalsSubtitle = tNav('palette.arrivalsHint');
        if (matchesAction(arrivalsTitle, arrivalsSubtitle, ['arrivals', 'arriving', 'checkin'])) {
          actionItems.push({
            key: 'act-arrivals',
            title: arrivalsTitle,
            subtitle: arrivalsSubtitle,
            icon: <FlightLandIcon sx={{ fontSize: 16 }} />,
            route: '/bookings?view=arriving',
          });
        }

        const departuresTitle = tNav('mobile.todaysDepartures');
        const departuresSubtitle = tNav('palette.departuresHint');
        if (matchesAction(departuresTitle, departuresSubtitle, ['departures', 'departing', 'checkout'])) {
          actionItems.push({
            key: 'act-departures',
            title: departuresTitle,
            subtitle: departuresSubtitle,
            icon: <FlightTakeoffIcon sx={{ fontSize: 16 }} />,
            route: '/bookings?view=departing',
          });
        }

        const paymentTitle = tNav('mobile.collectPayment');
        const paymentSubtitle = tNav('palette.collectPaymentHint');
        if (matchesAction(paymentTitle, paymentSubtitle, ['payment', 'collect', 'balance', 'overdue'])) {
          actionItems.push({
            key: 'act-collect-payment',
            title: paymentTitle,
            subtitle: paymentSubtitle,
            icon: <PaymentsIcon sx={{ fontSize: 16 }} />,
            route: '/bookings?view=balance',
          });
        }
      }

      if (canOpen('room-management')) {
        const vacantTitle = tNav('palette.availableRooms');
        const vacantSubtitle = tNav('palette.availableRoomsHint');
        if (matchesAction(vacantTitle, vacantSubtitle, ['vacant', 'available', 'rooms'])) {
          actionItems.push({
            key: 'act-available-rooms',
            title: vacantTitle,
            subtitle: vacantSubtitle,
            icon: <MeetingRoomOutlinedIcon sx={{ fontSize: 16 }} />,
            route: '/room-management?status=available',
          });
        }

        const dirtyTitle = tNav('palette.dirtyRooms');
        const dirtySubtitle = tNav('palette.dirtyRoomsHint');
        if (matchesAction(dirtyTitle, dirtySubtitle, ['dirty', 'clean', 'housekeeping', 'rooms'])) {
          actionItems.push({
            key: 'act-dirty-rooms',
            title: dirtyTitle,
            subtitle: dirtySubtitle,
            icon: <CleaningServicesOutlinedIcon sx={{ fontSize: 16 }} />,
            route: '/room-management?status=dirty',
          });
        }
      }

      if (canOpen('housekeeping')) {
        const taskTitle = tNav('mobile.newTask');
        if (matchesAction(taskTitle, undefined, ['task', 'housekeeping', 'clean'])) {
          actionItems.push({
            key: 'act-new-task',
            title: taskTitle,
            icon: <AddTaskIcon sx={{ fontSize: 16 }} />,
            route: '/housekeeping?create=task',
          });
        }

        const issueTitle = tNav('mobile.reportIssue');
        if (matchesAction(issueTitle, undefined, ['issue', 'maintenance', 'ticket', 'repair'])) {
          actionItems.push({
            key: 'act-report-issue',
            title: issueTitle,
            icon: <BuildIcon sx={{ fontSize: 16 }} />,
            route: '/housekeeping?create=ticket',
          });
        }
      }

      if (canOpen('guest-directory') || canOpen('guest-relations')) {
        const guestTitle = tNav('mobile.newGuest');
        if (matchesAction(guestTitle, undefined, ['guest', 'directory', 'profile'])) {
          actionItems.push({
            key: 'act-new-guest',
            title: guestTitle,
            icon: <PersonAddIcon sx={{ fontSize: 16 }} />,
            route: '/guest-relations/guests',
          });
        }
      }

      if (themeCtx) {
        const isDark = themeCtx.themeMode === 'dark';
        const themeTitle = isDark ? tNav('palette.switchLightMode') : tNav('palette.switchDarkMode');
        const themeSubtitle = tNav('palette.themeHint');
        if (matchesAction(themeTitle, themeSubtitle, ['theme', 'dark', 'light', 'appearance', 'mode'])) {
          actionItems.push({
            key: 'act-toggle-theme',
            title: themeTitle,
            subtitle: themeSubtitle,
            icon: isDark ? <LightModeOutlinedIcon sx={{ fontSize: 16 }} /> : <DarkModeOutlinedIcon sx={{ fontSize: 16 }} />,
            action: () => themeCtx.onThemeModeChange(isDark ? 'light' : 'dark'),
          });
        }
      }

      if (actionItems.length > 0) {
        out.push({
          key: 'actions',
          label: tNav('palette.groups.actions'),
          items: actionItems,
        });
      }
    }

    serverGroups.forEach((g) => {
      out.push({
        key: g.type,
        // intentional: dynamic key — localized header for the known types; the
        // server's English label is the fallback for any type the bundle doesn't map.
        label: tOr(`palette.scopes.${g.type}`, g.label),
        items: g.results.map((h) => ({
          key: `${g.type}-${h.id}`,
          title: h.title,
          subtitle: h.subtitle,
          icon: serverGroupIcon(g.type),
          route: h.route,
        })),
      });
    });

    if (showClient) {
      const pages = visibleItems
        .filter((item) => {
          const label = navLabelFor(item);
          return (
            !lowTerm ||
            label.toLowerCase().includes(lowTerm) ||
            item.path.toLowerCase().includes(lowTerm)
          );
        })
        .slice(0, term ? 6 : 12)
        .map((item) => ({
          key: `pg-${item.id}`,
          title: navLabelFor(item),
          subtitle: item.path,
          icon: renderNavIcon(item, 16) || dot,
          route: item.path,
        }));
      if (pages.length) out.push({ key: 'pages', label: tNav('palette.groups.pages'), items: pages });
    }

    // Help articles are client-side: the same weighted search that powers the
    // Help Centre, so ⌘K can surface guides without a backend endpoint.
    const showHelp = !isGuest && (scope === 'all' || scope === 'pages' || scope === 'help');
    if (showHelp && lowTerm.length >= 2) {
      const helpItems = searchArticles(helpArticles, lowTerm, 5).map((hit) => ({
        key: `help-${hit.article.slug}`,
        title: hit.article.title,
        subtitle: hit.article.summary,
        icon: <HelpOutlineIcon sx={{ fontSize: 16 }} />,
        route: `/help/${hit.article.slug}`,
      }));
      if (helpItems.length) out.push({ key: 'help', label: tNav('palette.groups.help'), items: helpItems });
    }

    return out;
  }, [term, lowTerm, slash, scope, visibleRecents, serverGroups, visibleItems, dot, serverGroupIcon, renderNavIcon, isGuest, navLabelFor, helpArticles, themeCtx, tNav, tOr]);

  const flatItems = React.useMemo(() => groups.flatMap((g) => g.items), [groups]);
  const safeActiveIndex = flatItems.length === 0 ? 0 : Math.min(activeIndex, flatItems.length - 1);

  React.useEffect(() => {
    if (!cmdOpen) return;
    const activeEl = listboxRef.current?.querySelector(`[data-palette-idx="${safeActiveIndex}"]`);
    if (activeEl && typeof activeEl.scrollIntoView === 'function') {
      activeEl.scrollIntoView({ block: 'nearest' });
    }
  }, [cmdOpen, safeActiveIndex]);

  const select = (item: SelectItem) => {
    if (item.action) {
      closeCmd();
      item.action();
      return;
    }
    const route = routeForSelection(item);
    if (!route) return;
    if (!isGuest) {
      persistRecent({ title: item.title, subtitle: item.subtitle, route, kind: 'nav' });
    }
    closeCmd();
    navigate(route);
  };

  const onPaletteKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      closeCmd();
    } else if (e.key === 'Tab' && !isGuest) {
      e.preventDefault();
      const currentIdx = PALETTE_SCOPES.indexOf(scope);
      const nextIdx = (currentIdx + (e.shiftKey ? -1 : 1) + PALETTE_SCOPES.length) % PALETTE_SCOPES.length;
      setScope(PALETTE_SCOPES[nextIdx]);
      setActiveIndex(0);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => Math.min(Math.max(0, flatItems.length - 1), i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      const item = flatItems[safeActiveIndex] || flatItems[0];
      if (item) select(item);
    }
  };

  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        openCmd();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [openCmd]);

  const paletteApi = React.useMemo(() => ({ open: openCmd }), [openCmd]);

  return (
    <PaletteContext.Provider value={paletteApi}>
      {children}
      <Popover
        open={cmdOpen}
        anchorReference="anchorPosition"
        anchorPosition={{ top: 72, left: viewportCenterX }}
        onClose={closeCmd}
        transformOrigin={{ vertical: 'top', horizontal: 'center' }}
        marginThreshold={16}
        slotProps={{ paper: { sx: { mt: 1, width: 'min(620px, 92vw)', borderRadius: 2, overflow: 'hidden' } } }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.75, py: 1.25, borderBottom: '1px solid', borderColor: 'divider' }}>
          <SearchIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
          <InputBase
            inputRef={cmdInputRef}
            value={cmdQuery}
            onChange={(e) => {
              setCmdQuery(e.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={onPaletteKeyDown}
            placeholder={isGuest ? tNav('palette.placeholderGuest') : tNav('palette.placeholder')}
            sx={{ flex: 1, fontSize: '0.9rem' }}
          />
          {serverLoading && <CircularProgress size={14} />}
          <Box sx={{ fontFamily: 'monospace', fontSize: '0.66rem', px: 0.875, py: '2px', borderRadius: 0.75, bgcolor: 'action.hover', color: 'text.secondary', fontWeight: 600 }}>
            esc
          </Box>
        </Box>

        {!isGuest && (
          <Box sx={{ display: 'flex', gap: 0.75, px: 1.5, py: 1, borderBottom: '1px solid', borderColor: 'divider', flexWrap: 'wrap' }}>
            {PALETTE_SCOPES.map((k) => {
              const on = scope === k;
              return (
                <Box
                  key={k}
                  component="button"
                  type="button"
                  onClick={() => {
                    setScope(k);
                    setActiveIndex(0);
                  }}
                  sx={{
                    px: 1.25, py: 0.5, borderRadius: 999, border: '1px solid',
                    borderColor: on ? 'text.primary' : 'divider', cursor: 'pointer',
                    fontSize: '0.72rem', fontWeight: 600,
                    color: on ? 'background.paper' : 'text.secondary',
                    bgcolor: on ? 'text.primary' : 'transparent',
                  }}
                >
                  {tNav(`palette.scopes.${k}`)}
                </Box>
              );
            })}
          </Box>
        )}

        <Box
          ref={listboxRef}
          role="listbox"
          aria-label={tNav('aria.searchResults')}
          sx={{ maxHeight: 380, overflowY: 'auto', py: 0.5 }}
        >
          {flatItems.length === 0 && (
            <Box sx={{ px: 2, py: 4, textAlign: 'center', color: 'text.secondary', fontSize: '0.85rem' }}>
              {term.length >= 2 ? tNav('palette.noMatches') : serverLoading ? tNav('common:state.searching') : tNav('palette.hint')}
            </Box>
          )}
          {(() => {
            let idx = -1;
            return groups.map((g) => (
              <Box key={g.key}>
                <Typography sx={{ px: 1.75, pt: 1, pb: 0.5, fontSize: '0.66rem', fontWeight: 700, color: 'text.secondary', textTransform: 'uppercase', letterSpacing: 0.6 }}>
                  {g.label}
                </Typography>
                {g.items.map((item) => {
                  idx += 1;
                  const myIdx = idx;
                  const active = myIdx === safeActiveIndex;
                  return (
                    <Box
                      key={item.key}
                      data-palette-idx={myIdx}
                      role="option"
                      aria-selected={active}
                      onMouseEnter={() => setActiveIndex(myIdx)}
                      onClick={() => select(item)}
                      sx={{
                        display: 'flex', alignItems: 'center', gap: 1.25, px: 1.75, py: 1, cursor: 'pointer',
                        bgcolor: active ? 'action.selected' : 'transparent',
                      }}
                    >
                      <Box sx={{ display: 'grid', placeItems: 'center', width: 26, height: 26, borderRadius: 1, bgcolor: 'action.hover', color: 'text.secondary', flexShrink: 0 }}>
                        {item.icon}
                      </Box>
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontSize: '0.86rem', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {item.title}
                        </Typography>
                        {item.subtitle && (
                          <Typography sx={{ fontSize: '0.72rem', color: 'text.secondary', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {item.subtitle}
                          </Typography>
                        )}
                      </Box>
                      {active && (
                        <Box sx={{ fontFamily: 'monospace', fontSize: '0.62rem', color: 'text.secondary' }}>↵</Box>
                      )}
                    </Box>
                  );
                })}
              </Box>
            ));
          })()}
        </Box>

        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.75,
            flexWrap: 'wrap',
            px: 1.75,
            py: 0.75,
            borderTop: '1px solid',
            borderColor: 'divider',
            bgcolor: 'action.hover',
            color: 'text.secondary',
            fontSize: '0.7rem',
          }}
        >
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
            <Box component="kbd" sx={{ fontFamily: 'monospace', fontSize: '0.65rem', px: 0.6, py: '1px', borderRadius: 0.5, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>↑↓</Box>
            <span>{tNav('palette.footerNavigate')}</span>
          </Box>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
            <Box component="kbd" sx={{ fontFamily: 'monospace', fontSize: '0.65rem', px: 0.6, py: '1px', borderRadius: 0.5, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>↵</Box>
            <span>{tNav('palette.footerSelect')}</span>
          </Box>
          {!isGuest && (
            <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
              <Box component="kbd" sx={{ fontFamily: 'monospace', fontSize: '0.65rem', px: 0.6, py: '1px', borderRadius: 0.5, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>⇥</Box>
              <span>{tNav('palette.footerScope')}</span>
            </Box>
          )}
        </Box>
      </Popover>
    </PaletteContext.Provider>
  );
};
