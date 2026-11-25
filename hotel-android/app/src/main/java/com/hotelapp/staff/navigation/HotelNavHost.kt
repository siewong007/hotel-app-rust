package com.hotelapp.staff.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KingBed
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KingBed
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.hotelapp.staff.AppContainer
import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.StaffSession
import com.hotelapp.staff.core.design.EmptyStateView
import com.hotelapp.staff.core.design.HotelLogoMark
import com.hotelapp.staff.core.design.HotelSnackbarHost
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.HotelTooltipIconButton
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.core.design.rememberReducedMotion
import com.hotelapp.staff.core.network.RealtimeConnectionState
import com.hotelapp.staff.core.ui.rememberViewModel
import com.hotelapp.staff.feature.auth.AuthViewModel
import com.hotelapp.staff.feature.auth.LoginScreen
import com.hotelapp.staff.feature.auth.SplashScreen
import com.hotelapp.staff.feature.bookings.BookingDetailScreen
import com.hotelapp.staff.feature.bookings.BookingDetailViewModel
import com.hotelapp.staff.feature.bookings.BookingsScreen
import com.hotelapp.staff.feature.bookings.BookingsViewModel
import com.hotelapp.staff.feature.bookings.CreateBookingSheet
import com.hotelapp.staff.feature.dashboard.DashboardScreen
import com.hotelapp.staff.feature.dashboard.DashboardViewModel
import com.hotelapp.staff.feature.ekyc.EkycQueueScreen
import com.hotelapp.staff.feature.ekyc.EkycViewModel
import com.hotelapp.staff.feature.guests.GuestDetailScreen
import com.hotelapp.staff.feature.guests.GuestDetailViewModel
import com.hotelapp.staff.feature.guests.GuestsScreen
import com.hotelapp.staff.feature.guests.GuestsViewModel
import com.hotelapp.staff.feature.housekeeping.HousekeepingScreen
import com.hotelapp.staff.feature.housekeeping.HousekeepingViewModel
import com.hotelapp.staff.feature.maintenance.MaintenanceScreen
import com.hotelapp.staff.feature.maintenance.MaintenanceViewModel
import com.hotelapp.staff.feature.more.MoreScreen
import com.hotelapp.staff.feature.more.MoreViewModel
import com.hotelapp.staff.feature.notifications.NotificationsModalSheet
import com.hotelapp.staff.feature.notifications.NotificationsViewModel
import com.hotelapp.staff.feature.payments.PendingPaymentsScreen
import com.hotelapp.staff.feature.payments.PendingPaymentsViewModel
import com.hotelapp.staff.feature.rooms.RoomsScreen
import com.hotelapp.staff.feature.rooms.RoomsViewModel
import com.hotelapp.staff.feature.search.SearchScreen
import com.hotelapp.staff.feature.search.SearchViewModel
import com.hotelapp.staff.feature.server.ServerConfigScreen
import com.hotelapp.staff.widget.WidgetRefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class NavTab(
    val routeClass: KClass<*>,
    val destination: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
fun HotelAppRoot(
    container: AppContainer,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    pendingDeepLink: StateFlow<String?> = remember { MutableStateFlow(null) },
    onConsumeDeepLink: () -> Unit = {},
    deferDeepLinks: Boolean = false,
) {
    val authState by container.sessionManager.authState.collectAsStateWithLifecycle()
    val serverConfig by container.serverConfigStore.config.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = authState,
        contentKey = { it::class },
        transitionSpec = {
            fadeIn(tween(260)) togetherWith fadeOut(tween(180))
        },
        label = "auth_root_transition",
    ) { state ->
        when (state) {
            AuthState.Checking -> SplashScreen(hotelName = serverConfig.hotelName)
            AuthState.Unauthenticated -> UnauthenticatedNavHost(container)
            is AuthState.Authenticated -> AuthenticatedNavHost(
                container = container,
                session = state.session,
                windowWidthSizeClass = windowWidthSizeClass,
                pendingDeepLink = pendingDeepLink,
                onConsumeDeepLink = onConsumeDeepLink,
                deferDeepLinks = deferDeepLinks,
            )
        }
    }
}

@Composable
private fun UnauthenticatedNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val authViewModel = rememberViewModel(key = "auth_vm") {
        AuthViewModel(container.authRepository)
    }

    NavHost(
        navController = navController,
        startDestination = LoginRoute,
        enterTransition = { slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(180)) { it / 6 } + fadeOut(tween(160)) },
    ) {
        composable<LoginRoute> {
            LoginScreen(
                viewModel = authViewModel,
                serverConfigStore = container.serverConfigStore,
                onOpenServerConfig = { navController.navigate(ServerConfigRoute) },
                credentialHelper = container.credentialHelper,
            )
        }
        composable<ServerConfigRoute> {
            ServerConfigScreen(
                serverConfigStore = container.serverConfigStore,
                serverRepository = container.serverRepository,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedNavHost(
    container: AppContainer,
    session: StaffSession,
    windowWidthSizeClass: WindowWidthSizeClass,
    pendingDeepLink: StateFlow<String?>,
    onConsumeDeepLink: () -> Unit,
    deferDeepLinks: Boolean = false,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val serverConfig by container.serverConfigStore.config.collectAsStateWithLifecycle()
    val wsState by container.realtimeSocketClient.connectionState.collectAsStateWithLifecycle()
    val isOnline by container.networkMonitor.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by container.offlineOpsCoordinator.pendingCount.collectAsStateWithLifecycle()
    val incomingDeepLink by pendingDeepLink.collectAsStateWithLifecycle()

    val notificationsViewModel = rememberViewModel(key = "notif_${session.user.id}") {
        NotificationsViewModel(container.notificationsRepository, container.realtimeSocketClient)
    }
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val useNavigationRail = windowWidthSizeClass != WindowWidthSizeClass.Compact
    val isExpandedWidth = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = remember(snackbarHostState, scope) {
        { message ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val tabs = remember(session) {
        buildList {
            add(
                NavTab(
                    routeClass = DashboardRoute::class,
                    destination = DashboardRoute,
                    label = "Dashboard",
                    selectedIcon = Icons.Filled.Dashboard,
                    unselectedIcon = Icons.Outlined.Dashboard,
                ),
            )
            if (session.hasPermission("bookings:read")) {
                add(
                    NavTab(
                        routeClass = BookingsRoute::class,
                        destination = BookingsRoute("all"),
                        label = "Bookings",
                        selectedIcon = Icons.Filled.CalendarMonth,
                        unselectedIcon = Icons.Outlined.CalendarMonth,
                    ),
                )
            }
            if (session.hasPermission("rooms:read")) {
                add(
                    NavTab(
                        routeClass = RoomsRoute::class,
                        destination = RoomsRoute,
                        label = "Rooms",
                        selectedIcon = Icons.Filled.KingBed,
                        unselectedIcon = Icons.Outlined.KingBed,
                    ),
                )
            }
            if (session.hasPermission("housekeeping:read")) {
                add(
                    NavTab(
                        routeClass = HousekeepingRoute::class,
                        destination = HousekeepingRoute,
                        label = "Housekeeping",
                        selectedIcon = Icons.Filled.CleaningServices,
                        unselectedIcon = Icons.Outlined.CleaningServices,
                    ),
                )
            }
            add(
                NavTab(
                    routeClass = MoreRoute::class,
                    destination = MoreRoute,
                    label = "More",
                    selectedIcon = Icons.Filled.GridView,
                    unselectedIcon = Icons.Outlined.GridView,
                ),
            )
        }
    }

    val isTopTab = tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }

    fun navigateTopTab(target: Any) {
        haptics.tick()
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(incomingDeepLink, session, deferDeepLinks) {
        if (deferDeepLinks) return@LaunchedEffect
        val raw = incomingDeepLink ?: return@LaunchedEffect
        val parsed = DeepLinkRouter.parse(raw)
        if (parsed != null) {
            if (!DeepLinkRouter.canAccess(parsed, session)) {
                showMessage("Your role does not have permission to open that section.")
            } else if (parsed == DashboardRoute || parsed == RoomsRoute || parsed == HousekeepingRoute || parsed == MoreRoute || (parsed is BookingsRoute && parsed.view == "all")) {
                navigateTopTab(parsed)
            } else {
                navController.navigate(parsed) {
                    launchSingleTop = true
                }
            }
        }
        onConsumeDeepLink()
    }

    val detailEnter = slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220))
    val detailExit = fadeOut(tween(150))
    val detailPopEnter = fadeIn(tween(180))
    val detailPopExit = slideOutHorizontally(tween(180)) { it / 6 } + fadeOut(tween(160))

    Scaffold(
        containerColor = colors.surfaceApp,
        contentWindowInsets = if (isTopTab) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        snackbarHost = { HotelSnackbarHost(snackbarHostState) },
        topBar = {
            if (isTopTab) {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                HotelLogoMark(size = 28.dp)
                                Text(
                                    text = serverConfig.hotelName,
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        },
                        actions = {
                            if (pendingSyncCount > 0) {
                                AssistChip(
                                    onClick = {
                                        haptics.click()
                                        container.triggerSyncNow()
                                        showMessage("Syncing $pendingSyncCount queued update(s)...")
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    label = {
                                        Text(
                                            text = "Queued $pendingSyncCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = colors.warning.bg,
                                        labelColor = colors.warning.fg,
                                    ),
                                    border = BorderStroke(1.dp, colors.warning.border),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            RealtimeStatusChip(
                                isOnline = isOnline,
                                state = wsState,
                                onClick = {
                                    haptics.click()
                                    if (!isOnline) {
                                        showMessage("No network connection. Live sync resumes automatically when online.")
                                    } else if (wsState == RealtimeConnectionState.DISCONNECTED) {
                                        container.realtimeSocketClient.reconnectNow()
                                        showMessage("Reconnecting live updates...")
                                    } else {
                                        val label = if (wsState == RealtimeConnectionState.CONNECTED) {
                                            "Live sync active"
                                        } else {
                                            "Connecting live updates..."
                                        }
                                        showMessage(label)
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            val canUseSearch = session.hasAnyPermission("bookings:read", "guests:read", "rooms:read", "ledgers:read")
                            if (canUseSearch) {
                                HotelTooltipIconButton(
                                    tooltip = "Global Search",
                                    onClick = {
                                        haptics.click()
                                        navController.navigate(SearchRoute) { launchSingleTop = true }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search",
                                        tint = colors.textPrimary,
                                    )
                                }
                            }
                            HotelTooltipIconButton(
                                tooltip = "Notifications",
                                onClick = {
                                    haptics.click()
                                    notificationsViewModel.refresh(silent = true)
                                    showNotificationsSheet = true
                                },
                            ) {
                                val badgeDesc = if (notificationsState.unreadCount > 0) {
                                    "Notifications, ${notificationsState.unreadCount} unread"
                                } else {
                                    "Notifications"
                                }
                                BadgedBox(
                                    badge = {
                                        if (notificationsState.unreadCount > 0) {
                                            Badge(
                                                containerColor = colors.primaryMain,
                                                contentColor = colors.primaryContrastText,
                                            ) {
                                                Text(notificationsState.unreadCount.coerceAtMost(99).toString())
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = badgeDesc,
                                        tint = colors.textPrimary,
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceBase),
                    )
                    HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            if (isTopTab && !useNavigationRail) {
                Column {
                    HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
                    NavigationBar(
                        containerColor = colors.surfaceBase,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            val selected = destination?.hasRoute(tab.routeClass) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navigateTopTab(tab.destination)
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.primaryOnSurface,
                                    selectedTextColor = colors.primaryOnSurface,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary,
                                    indicatorColor = colors.primarySubtle,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTopTab) padding else PaddingValues(0.dp)),
        ) {
            if (isTopTab && useNavigationRail) {
                NavigationRail(
                    containerColor = colors.surfaceBase,
                ) {
                    Spacer(modifier = Modifier.weight(0.15f))
                    tabs.forEach { tab ->
                        val selected = destination?.hasRoute(tab.routeClass) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (!selected) navigateTopTab(tab.destination)
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.primaryOnSurface,
                                selectedTextColor = colors.primaryOnSurface,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary,
                                indicatorColor = colors.primarySubtle,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                VerticalDivider(color = colors.borderSubtle, thickness = 1.dp)
            }

            NavHost(
                navController = navController,
                startDestination = DashboardRoute,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(140)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                composable<DashboardRoute>(
                    deepLinks = listOf(navDeepLink<DashboardRoute>(basePath = "hotelstaff://dashboard")),
                ) {
                    val vm = rememberViewModel(key = "dash_${session.user.id}") {
                        DashboardViewModel(
                            session = session,
                            bookingsRepository = container.bookingsRepository,
                            roomsRepository = container.roomsRepository,
                            realtimeSocketClient = container.realtimeSocketClient,
                            operationsRepository = container.operationsRepository,
                            paymentsRepository = container.paymentsRepository,
                            ekycRepository = container.ekycRepository,
                            hotelNameProvider = { container.serverConfigStore.config.value.hotelName },
                            onWidgetSnapshotReady = { snapshot ->
                                WidgetRefreshWorker.saveAndPush(
                                    context = container.application,
                                    localStore = container.localStore,
                                    snapshot = snapshot,
                                )
                            },
                        )
                    }
                    DashboardScreen(
                        session = session,
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onOpenBookingsView = { view ->
                            navController.navigate(BookingsRoute(view)) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                            }
                        },
                        onOpenBookingDetail = { id -> navController.navigate(BookingDetailRoute(id)) },
                        onOpenRooms = { navigateTopTab(RoomsRoute) },
                        onOpenHousekeeping = { navigateTopTab(HousekeepingRoute) },
                        onOpenMaintenance = { navController.navigate(MaintenanceRoute) },
                        onOpenGuests = { navController.navigate(GuestsRoute) },
                        onOpenPendingPayments = { navController.navigate(PendingPaymentsRoute) },
                        onOpenEkycQueue = { navController.navigate(EkycQueueRoute) },
                    )
                }

                composable<BookingsRoute>(
                    deepLinks = listOf(navDeepLink<BookingsRoute>(basePath = "hotelstaff://bookings")),
                ) { entry ->
                    val route = entry.toRoute<BookingsRoute>()
                    val vm = rememberViewModel(key = "bookings_${session.user.id}_${route.view}") {
                        BookingsViewModel(
                            initialView = route.view,
                            repository = container.bookingsRepository,
                            realtimeSocketClient = container.realtimeSocketClient,
                        )
                    }
                    var showCreateBookingSheet by remember { mutableStateOf(false) }
                    val canCreateBooking = session.hasPermission("bookings:create")
                    if (isExpandedWidth) {
                        var selectedBookingId by remember { mutableStateOf<Long?>(null) }
                        Row(modifier = Modifier.fillMaxSize()) {
                            BookingsScreen(
                                viewModel = vm,
                                serverConfigStore = container.serverConfigStore,
                                onOpenBookingDetail = { id -> selectedBookingId = id },
                                modifier = Modifier.weight(0.44f),
                                canCreateBooking = canCreateBooking,
                                onOpenCreateBooking = { showCreateBookingSheet = true },
                            )
                            VerticalDivider(color = colors.borderSubtle, thickness = 1.dp)
                            Box(modifier = Modifier.weight(0.56f).fillMaxHeight()) {
                                val detailId = selectedBookingId
                                if (detailId == null) {
                                    EmptyStateView(
                                        title = "Select a booking",
                                        subtitle = "Choose any reservation from the list to inspect folio and guest details.",
                                        icon = Icons.Outlined.TouchApp,
                                    )
                                } else {
                                    val detailVm = rememberViewModel(key = "booking_pane_$detailId") {
                                        BookingDetailViewModel(
                                            bookingId = detailId,
                                            repository = container.bookingsRepository,
                                            paymentsRepository = container.paymentsRepository,
                                            canReadPayments = session.hasPermission("payments:read"),
                                        )
                                    }
                                    BookingDetailScreen(
                                        session = session,
                                        viewModel = detailVm,
                                        serverConfigStore = container.serverConfigStore,
                                        onBack = null,
                                        onOpenGuest = { guestId -> navController.navigate(GuestDetailRoute(guestId)) },
                                    )
                                }
                            }
                        }
                    } else {
                        BookingsScreen(
                            viewModel = vm,
                            serverConfigStore = container.serverConfigStore,
                            onOpenBookingDetail = { id -> navController.navigate(BookingDetailRoute(id)) },
                            canCreateBooking = canCreateBooking,
                            onOpenCreateBooking = { showCreateBookingSheet = true },
                        )
                    }

                    if (showCreateBookingSheet) {
                        CreateBookingSheet(
                            session = session,
                            currency = serverConfig.currency,
                            bookingsRepository = container.bookingsRepository,
                            guestsRepository = container.guestsRepository,
                            roomsRepository = container.roomsRepository,
                            onDismiss = { showCreateBookingSheet = false },
                            onCreated = { createdId ->
                                showCreateBookingSheet = false
                                vm.refresh(silent = true)
                                navController.navigate(BookingDetailRoute(createdId))
                            },
                        )
                    }
                }

                composable<BookingDetailRoute>(
                    deepLinks = listOf(navDeepLink<BookingDetailRoute>(basePath = "hotelstaff://booking")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) { entry ->
                    val route = entry.toRoute<BookingDetailRoute>()
                    val vm = rememberViewModel(key = "booking_${route.bookingId}") {
                        BookingDetailViewModel(
                            bookingId = route.bookingId,
                            repository = container.bookingsRepository,
                            paymentsRepository = container.paymentsRepository,
                            canReadPayments = session.hasPermission("payments:read"),
                        )
                    }
                    BookingDetailScreen(
                        session = session,
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onBack = { navController.popBackStack() },
                        onOpenGuest = { guestId -> navController.navigate(GuestDetailRoute(guestId)) },
                    )
                }

                composable<RoomsRoute>(
                    deepLinks = listOf(navDeepLink<RoomsRoute>(basePath = "hotelstaff://rooms")),
                ) {
                    val vm = rememberViewModel(key = "rooms_${session.user.id}") {
                        RoomsViewModel(
                            repository = container.roomsRepository,
                            realtimeSocketClient = container.realtimeSocketClient,
                            offlineOps = container.offlineOpsCoordinator,
                        )
                    }
                    RoomsScreen(
                        session = session,
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onOpenBookingDetail = { id -> navController.navigate(BookingDetailRoute(id)) },
                        onShowMessage = showMessage,
                    )
                }

                composable<HousekeepingRoute>(
                    deepLinks = listOf(navDeepLink<HousekeepingRoute>(basePath = "hotelstaff://housekeeping")),
                ) {
                    val vm = rememberViewModel(key = "hk_${session.user.id}") {
                        HousekeepingViewModel(
                            session = session,
                            repository = container.housekeepingRepository,
                            realtimeSocketClient = container.realtimeSocketClient,
                            offlineOps = container.offlineOpsCoordinator,
                        )
                    }
                    HousekeepingScreen(
                        session = session,
                        viewModel = vm,
                        onShowMessage = showMessage,
                    )
                }

                composable<MoreRoute>(
                    deepLinks = listOf(navDeepLink<MoreRoute>(basePath = "hotelstaff://more")),
                ) {
                    val vm = rememberViewModel(key = "more_${session.user.id}") {
                        MoreViewModel(container.authRepository, container.serverConfigStore)
                    }
                    MoreScreen(
                        session = session,
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onOpenMaintenance = { navController.navigate(MaintenanceRoute) },
                        onOpenGuests = { navController.navigate(GuestsRoute) },
                        onOpenServerConfig = { navController.navigate(ServerConfigRoute) },
                        onShowMessage = showMessage,
                        onOpenPendingPayments = { navController.navigate(PendingPaymentsRoute) },
                        onOpenEkycQueue = { navController.navigate(EkycQueueRoute) },
                        pendingSyncCount = pendingSyncCount,
                        onSyncNow = { container.triggerSyncNow() },
                        staffNotificationManager = container.staffNotificationManager,
                    )
                }

                composable<SearchRoute>(
                    deepLinks = listOf(navDeepLink<SearchRoute>(basePath = "hotelstaff://search")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) {
                    val vm = rememberViewModel(key = "search_${session.user.id}") {
                        SearchViewModel(container.searchRepository, container.serverConfigStore)
                    }
                    SearchScreen(
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onBack = { navController.popBackStack() },
                        onOpenBookingDetail = { id ->
                            if (session.hasPermission("bookings:read")) {
                                navController.navigate(BookingDetailRoute(id))
                            }
                        },
                        onOpenGuestDetail = { id ->
                            if (session.hasPermission("guests:read")) {
                                navController.navigate(GuestDetailRoute(id))
                            }
                        },
                        onOpenRooms = {
                            if (session.hasPermission("rooms:read")) {
                                navigateTopTab(RoomsRoute)
                            }
                        },
                        session = session,
                        onShowMessage = showMessage,
                    )
                }

                composable<PendingPaymentsRoute>(
                    deepLinks = listOf(navDeepLink<PendingPaymentsRoute>(basePath = "hotelstaff://payments/pending")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) {
                    val vm = rememberViewModel(key = "pending_pay_${session.user.id}") {
                        PendingPaymentsViewModel(container.paymentsRepository, container.realtimeSocketClient)
                    }
                    PendingPaymentsScreen(
                        session = session,
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onBack = { navController.popBackStack() },
                        onOpenBookingDetail = { id -> navController.navigate(BookingDetailRoute(id)) },
                    )
                }

                composable<EkycQueueRoute>(
                    deepLinks = listOf(navDeepLink<EkycQueueRoute>(basePath = "hotelstaff://ekyc")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) {
                    val vm = rememberViewModel(key = "ekyc_${session.user.id}") {
                        EkycViewModel(session, container.ekycRepository, container.realtimeSocketClient)
                    }
                    EkycQueueScreen(
                        session = session,
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<MaintenanceRoute>(
                    deepLinks = listOf(navDeepLink<MaintenanceRoute>(basePath = "hotelstaff://maintenance")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) {
                    val vm = rememberViewModel(key = "maint_${session.user.id}") {
                        MaintenanceViewModel(
                            session = session,
                            repository = container.maintenanceRepository,
                            housekeepingRepository = container.housekeepingRepository,
                            roomsRepository = container.roomsRepository,
                            realtimeSocketClient = container.realtimeSocketClient,
                            offlineOps = container.offlineOpsCoordinator,
                        )
                    }
                    MaintenanceScreen(
                        session = session,
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable<GuestsRoute>(
                    deepLinks = listOf(navDeepLink<GuestsRoute>(basePath = "hotelstaff://guests")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) {
                    val vm = rememberViewModel(key = "guests_${session.user.id}") {
                        GuestsViewModel(container.guestsRepository, container.realtimeSocketClient)
                    }
                    if (isExpandedWidth) {
                        var selectedGuestId by remember { mutableStateOf<Long?>(null) }
                        Row(modifier = Modifier.fillMaxSize()) {
                            GuestsScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onOpenGuestDetail = { id -> selectedGuestId = id },
                                modifier = Modifier.weight(0.44f),
                            )
                            VerticalDivider(color = colors.borderSubtle, thickness = 1.dp)
                            Box(modifier = Modifier.weight(0.56f).fillMaxHeight()) {
                                val guestId = selectedGuestId
                                if (guestId == null) {
                                    EmptyStateView(
                                        title = "Select a guest",
                                        subtitle = "Choose a guest from the directory to view their 360 profile.",
                                        icon = Icons.Outlined.TouchApp,
                                    )
                                } else {
                                    val detailVm = rememberViewModel(key = "guest_pane_$guestId") {
                                        GuestDetailViewModel(guestId, container.guestsRepository)
                                    }
                                    GuestDetailScreen(
                                        viewModel = detailVm,
                                        serverConfigStore = container.serverConfigStore,
                                        onBack = null,
                                        onOpenBookingDetail = { id -> navController.navigate(BookingDetailRoute(id)) },
                                        canReadBookings = session.hasPermission("bookings:read"),
                                    )
                                }
                            }
                        }
                    } else {
                        GuestsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenGuestDetail = { id -> navController.navigate(GuestDetailRoute(id)) },
                        )
                    }
                }

                composable<GuestDetailRoute>(
                    deepLinks = listOf(navDeepLink<GuestDetailRoute>(basePath = "hotelstaff://guest")),
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) { entry ->
                    val route = entry.toRoute<GuestDetailRoute>()
                    val vm = rememberViewModel(key = "guest_${route.guestId}") {
                        GuestDetailViewModel(route.guestId, container.guestsRepository)
                    }
                    GuestDetailScreen(
                        viewModel = vm,
                        serverConfigStore = container.serverConfigStore,
                        onBack = { navController.popBackStack() },
                        onOpenBookingDetail = { id -> navController.navigate(BookingDetailRoute(id)) },
                        canReadBookings = session.hasPermission("bookings:read"),
                    )
                }

                composable<ServerConfigRoute>(
                    enterTransition = { detailEnter },
                    exitTransition = { detailExit },
                    popEnterTransition = { detailPopEnter },
                    popExitTransition = { detailPopExit },
                ) {
                    ServerConfigScreen(
                        serverConfigStore = container.serverConfigStore,
                        serverRepository = container.serverRepository,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationsModalSheet(
            viewModel = notificationsViewModel,
            onDismiss = { showNotificationsSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealtimeStatusChip(
    isOnline: Boolean,
    state: RealtimeConnectionState,
    onClick: () -> Unit,
) {
    val colors = HotelTheme.colors
    val reducedMotion = rememberReducedMotion()
    val tone = when {
        !isOnline -> colors.danger
        state == RealtimeConnectionState.CONNECTED -> colors.success
        state == RealtimeConnectionState.CONNECTING -> colors.warning
        else -> colors.neutral
    }
    val label = when {
        !isOnline -> "No Net"
        state == RealtimeConnectionState.CONNECTED -> "Live"
        state == RealtimeConnectionState.CONNECTING -> "Syncing"
        else -> "Offline"
    }
    val tooltipText = when {
        !isOnline -> "Device offline — live updates pause until network returns"
        state == RealtimeConnectionState.CONNECTED -> "Live WebSocket updates active"
        state == RealtimeConnectionState.CONNECTING -> "Connecting to live updates"
        else -> "Live updates disconnected — tap to reconnect"
    }

    val pulseAlpha = if (isOnline && state == RealtimeConnectionState.CONNECTING && !reducedMotion) {
        val infinite = rememberInfiniteTransition(label = "ws_pulse")
        val a by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ws_dot_alpha",
        )
        a
    } else {
        1f
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip(
                containerColor = colors.surfaceOverlay,
                contentColor = colors.textPrimary,
            ) {
                Text(tooltipText, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = rememberTooltipState(),
    ) {
        AssistChip(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(tone.fg),
                )
            },
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = tone.bg,
                labelColor = tone.fg,
            ),
            border = BorderStroke(1.dp, tone.border),
        )
    }
}
