package com.hotelapp.staff

import android.app.Application
import com.hotelapp.staff.core.auth.AuthState
import com.hotelapp.staff.core.auth.SessionManager
import com.hotelapp.staff.core.auth.StaffCredentialHelper
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.network.AndroidKeystoreCipher
import com.hotelapp.staff.core.network.AndroidNetworkMonitor
import com.hotelapp.staff.core.network.EncryptedCookieJar
import com.hotelapp.staff.core.network.FileCookieStorage
import com.hotelapp.staff.core.network.HotelJson
import com.hotelapp.staff.core.network.NetworkFactory
import com.hotelapp.staff.core.network.NetworkMonitor
import com.hotelapp.staff.core.network.RealtimeSocketClient
import com.hotelapp.staff.core.network.runSuspendCatching
import com.hotelapp.staff.core.notifications.StaffNotificationManager
import com.hotelapp.staff.core.print.InvoicePdfPrinter
import com.hotelapp.staff.core.storage.HotelLocalDatabase
import com.hotelapp.staff.core.storage.HotelLocalStore
import com.hotelapp.staff.core.storage.LocalDataScope
import com.hotelapp.staff.core.sync.OfflineOpsCoordinator
import com.hotelapp.staff.core.sync.SyncPendingMutationsWorker
import com.hotelapp.staff.data.api.AuthApi
import com.hotelapp.staff.data.api.BookingsApi
import com.hotelapp.staff.data.api.EkycApi
import com.hotelapp.staff.data.api.GuestsApi
import com.hotelapp.staff.data.api.HousekeepingApi
import com.hotelapp.staff.data.api.MaintenanceApi
import com.hotelapp.staff.data.api.NotificationsApi
import com.hotelapp.staff.data.api.OperationsApi
import com.hotelapp.staff.data.api.PasskeyApi
import com.hotelapp.staff.data.api.PaymentsApi
import com.hotelapp.staff.data.api.RoomsApi
import com.hotelapp.staff.data.api.SearchApi
import com.hotelapp.staff.data.api.SettingsApi
import com.hotelapp.staff.data.repository.AuthRepository
import com.hotelapp.staff.data.repository.BookingsRepository
import com.hotelapp.staff.data.repository.DefaultAuthRepository
import com.hotelapp.staff.data.repository.DefaultBookingsRepository
import com.hotelapp.staff.data.repository.DefaultEkycRepository
import com.hotelapp.staff.data.repository.DefaultGuestsRepository
import com.hotelapp.staff.data.repository.DefaultHousekeepingRepository
import com.hotelapp.staff.data.repository.DefaultMaintenanceRepository
import com.hotelapp.staff.data.repository.DefaultNotificationsRepository
import com.hotelapp.staff.data.repository.DefaultOperationsRepository
import com.hotelapp.staff.data.repository.DefaultPaymentsRepository
import com.hotelapp.staff.data.repository.DefaultRoomsRepository
import com.hotelapp.staff.data.repository.DefaultSearchRepository
import com.hotelapp.staff.data.repository.DefaultServerRepository
import com.hotelapp.staff.data.repository.EkycRepository
import com.hotelapp.staff.data.repository.GuestsRepository
import com.hotelapp.staff.data.repository.HousekeepingRepository
import com.hotelapp.staff.data.repository.MaintenanceRepository
import com.hotelapp.staff.data.repository.NotificationsRepository
import com.hotelapp.staff.data.repository.OperationsRepository
import com.hotelapp.staff.data.repository.PaymentsRepository
import com.hotelapp.staff.data.repository.RoomsRepository
import com.hotelapp.staff.data.repository.SearchRepository
import com.hotelapp.staff.data.repository.ServerRepository
import com.hotelapp.staff.widget.WidgetRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

class AppContainer(val application: Application) {
    val serverConfigStore = ServerConfigStore(application)
    val sessionManager = SessionManager()
    val networkMonitor: NetworkMonitor = AndroidNetworkMonitor(application)

    private fun currentLocalScope(): LocalDataScope {
        val rawBase = serverConfigStore.currentBaseUrl()
        val origin = SessionManager.normalizeOrigin(rawBase) ?: rawBase
        val userId = (sessionManager.authState.value as? AuthState.Authenticated)?.session?.user?.id ?: 0L
        return LocalDataScope(serverOrigin = origin, userId = userId)
    }

    val localStore: HotelLocalStore = HotelLocalDatabase(
        context = application,
        scopeProvider = ::currentLocalScope,
        cipher = AndroidKeystoreCipher(keyAlias = "hotel_local_db_key"),
    )
    val credentialHelper = StaffCredentialHelper(application)
    val staffNotificationManager = StaffNotificationManager(
        context = application,
        serverConfigStore = serverConfigStore,
        sessionProvider = { (sessionManager.authState.value as? AuthState.Authenticated)?.session },
    )

    val offlineOpsCoordinator = OfflineOpsCoordinator(
        localStore = localStore,
        scheduleSyncWorker = { SyncPendingMutationsWorker.schedule(application, replace = false) },
        principalProvider = ::currentLocalScope,
        generationProvider = { sessionManager.currentGeneration() },
    )

    private val cookieJar = EncryptedCookieJar(
        storage = FileCookieStorage(File(application.filesDir, "cookies.enc")),
        cipher = AndroidKeystoreCipher(),
    )

    private val refreshClient = NetworkFactory.createRefreshClient(serverConfigStore, cookieJar)
    private val tokenRefresher = NetworkFactory.createTokenRefresher(
        sessionManager = sessionManager,
        refreshClient = refreshClient,
        baseUrlProvider = { serverConfigStore.currentBaseUrl() },
    )
    private val mainClient = NetworkFactory.createMainClient(
        serverConfigStore = serverConfigStore,
        cookieJar = cookieJar,
        sessionManager = sessionManager,
        refreshClient = refreshClient,
        tokenRefresher = tokenRefresher,
    )
    private val wsClient = NetworkFactory.createWebSocketClient(serverConfigStore)

    val realtimeSocketClient = RealtimeSocketClient(
        client = wsClient,
        baseUrlProvider = { serverConfigStore.currentBaseUrl() },
        sessionManager = sessionManager,
        isOnlineProvider = { networkMonitor.isOnline.value },
        networkOnlineFlow = networkMonitor.isOnline,
        onUnauthorizedOutcome = { failedToken -> tokenRefresher.refreshIfStale(failedToken) },
    )

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(NetworkFactory.PLACEHOLDER_BASE_URL)
        .client(mainClient)
        .addConverterFactory(HotelJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private val probeClient = NetworkFactory.createProbeClient()
    private val probeRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(NetworkFactory.PLACEHOLDER_BASE_URL)
        .client(probeClient)
        .addConverterFactory(HotelJson.asConverterFactory("application/json".toMediaType()))
        .build()
    private val probeSettingsApi: SettingsApi = probeRetrofit.create(SettingsApi::class.java)

    private val settingsApi: SettingsApi = retrofit.create(SettingsApi::class.java)
    private val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    private val passkeyApi: PasskeyApi = retrofit.create(PasskeyApi::class.java)
    private val bookingsApi: BookingsApi = retrofit.create(BookingsApi::class.java)
    private val roomsApi: RoomsApi = retrofit.create(RoomsApi::class.java)
    private val housekeepingApi: HousekeepingApi = retrofit.create(HousekeepingApi::class.java)
    private val maintenanceApi: MaintenanceApi = retrofit.create(MaintenanceApi::class.java)
    private val guestsApi: GuestsApi = retrofit.create(GuestsApi::class.java)
    private val notificationsApi: NotificationsApi = retrofit.create(NotificationsApi::class.java)
    private val searchApi: SearchApi = retrofit.create(SearchApi::class.java)
    private val paymentsApi: PaymentsApi = retrofit.create(PaymentsApi::class.java)
    private val ekycApi: EkycApi = retrofit.create(EkycApi::class.java)
    private val operationsApi: OperationsApi = retrofit.create(OperationsApi::class.java)

    private val sessionCleanupMutex = Mutex()
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _hasUnlockedInProcess = MutableStateFlow(false)
    val hasUnlockedInProcess: StateFlow<Boolean> = _hasUnlockedInProcess.asStateFlow()

    @Volatile
    var lastBackgroundedElapsedMs: Long = 0L

    @Volatile
    var hasTriggeredColdLock: Boolean = false

    fun setAppLocked(locked: Boolean) {
        _isAppLocked.value = locked
    }

    fun markUnlockedInProcess() {
        _hasUnlockedInProcess.value = true
    }

    fun resetLockState() {
        _isAppLocked.value = false
        _hasUnlockedInProcess.value = false
        lastBackgroundedElapsedMs = 0L
        hasTriggeredColdLock = false
    }

    fun cancelInFlightNetwork() {
        mainClient.dispatcher.cancelAll()
        refreshClient.dispatcher.cancelAll()
    }

    internal suspend fun clearTransientUserData() {
        runSuspendCatching { offlineOpsCoordinator.clearAll() }
        runSuspendCatching { serverConfigStore.clearRecentSearches() }
        runCatching { InvoicePdfPrinter.clearCachedInvoices(application) }
        runCatching { File(application.cacheDir, "receipts").deleteRecursively() }
        runCatching { staffNotificationManager.clearPostedAndPending() }
        runSuspendCatching {
            WidgetRefreshWorker.clearWidget(
                context = application,
                localStore = localStore,
                hotelName = serverConfigStore.config.value.hotelName,
            )
        }
    }

    internal suspend fun clearSessionData(onlyIfUnauthenticated: Boolean = false) {
        sessionCleanupMutex.withLock {
            if (onlyIfUnauthenticated && sessionManager.authState.value is AuthState.Authenticated) {
                return
            }
            realtimeSocketClient.stop()
            cancelInFlightNetwork()
            runCatching { SyncPendingMutationsWorker.cancel(application) }
            runCatching { cookieJar.clearAll() }
            clearTransientUserData()
            runSuspendCatching { serverConfigStore.setLastAuthenticatedPrincipal(null) }
        }
    }

    val serverRepository: ServerRepository = DefaultServerRepository(
        settingsApi = settingsApi,
        serverConfigStore = serverConfigStore,
        sessionManager = sessionManager,
        realtimeSocketClient = realtimeSocketClient,
        onServerChanged = { clearSessionData() },
        probeSettingsApi = probeSettingsApi,
        onBeforeServerChangeLogout = {
            if (sessionManager.authState.value is AuthState.Authenticated) {
                withTimeoutOrNull(2_000L) { authApi.logout() }
            }
        },
    )
    val authRepository: AuthRepository = DefaultAuthRepository(
        authApi = authApi,
        passkeyApi = passkeyApi,
        serverRepository = serverRepository,
        sessionManager = sessionManager,
        cookieJar = cookieJar,
        realtimeSocketClient = realtimeSocketClient,
        onLoggedOut = { clearSessionData() },
        onAuthenticatedUser = { userId ->
            val rawBase = serverConfigStore.currentBaseUrl()
            val origin = SessionManager.normalizeOrigin(rawBase) ?: rawBase
            val principal = "$origin|$userId"
            val previousPrincipal = serverConfigStore.getLastAuthenticatedPrincipal()
            if (previousPrincipal != principal) {
                clearTransientUserData()
            }
            runSuspendCatching { localStore.purgeOtherScopes(origin, userId) }
            serverConfigStore.setLastAuthenticatedPrincipal(principal)
        },
        currentServerUrlProvider = { serverConfigStore.currentBaseUrl() },
        shouldStartRealtime = { false },
        onInteractiveLogin = { markUnlockedInProcess() },
        cancelInFlightRequests = ::cancelInFlightNetwork,
    )
    val bookingsRepository: BookingsRepository = DefaultBookingsRepository(bookingsApi)
    val roomsRepository: RoomsRepository = DefaultRoomsRepository(roomsApi)
    val housekeepingRepository: HousekeepingRepository = DefaultHousekeepingRepository(housekeepingApi)
    val maintenanceRepository: MaintenanceRepository = DefaultMaintenanceRepository(maintenanceApi)
    val guestsRepository: GuestsRepository = DefaultGuestsRepository(guestsApi)
    val notificationsRepository: NotificationsRepository = DefaultNotificationsRepository(notificationsApi)
    val searchRepository: SearchRepository = DefaultSearchRepository(searchApi)
    val paymentsRepository: PaymentsRepository = DefaultPaymentsRepository(paymentsApi)
    val ekycRepository: EkycRepository = DefaultEkycRepository(ekycApi)
    val operationsRepository: OperationsRepository = DefaultOperationsRepository(operationsApi)

    fun triggerSyncNow() {
        SyncPendingMutationsWorker.schedule(application, replace = true)
    }
}

class HotelStaffApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        InvoicePdfPrinter.clearCachedInvoices(this)
        runCatching { File(cacheDir, "receipts").deleteRecursively() }
        container = AppContainer(this)
        container.staffNotificationManager.ensureChannels()
        WidgetRefreshWorker.schedulePeriodic(this)

        appScope.launch {
            var wasAuthenticated = false
            container.sessionManager.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        val newlyAuthenticated = !wasAuthenticated
                        wasAuthenticated = true
                        container.offlineOpsCoordinator.refreshPendingCount()
                        if (newlyAuthenticated &&
                            container.offlineOpsCoordinator.pendingCount.value > 0 &&
                            container.networkMonitor.isOnline.value
                        ) {
                            SyncPendingMutationsWorker.schedule(this@HotelStaffApp, replace = false)
                        }
                    }
                    AuthState.Unauthenticated -> {
                        if (wasAuthenticated) {
                            wasAuthenticated = false
                            container.clearSessionData(onlyIfUnauthenticated = true)
                        } else {
                            container.offlineOpsCoordinator.refreshPendingCount()
                        }
                    }
                    AuthState.Checking -> Unit
                }
            }
        }
        appScope.launch {
            var wasOnline = container.networkMonitor.isOnline.value
            container.networkMonitor.isOnline.collect { online ->
                if (online &&
                    !wasOnline &&
                    container.sessionManager.authState.value is AuthState.Authenticated &&
                    container.offlineOpsCoordinator.pendingCount.value > 0
                ) {
                    SyncPendingMutationsWorker.schedule(this@HotelStaffApp, replace = false)
                }
                wasOnline = online
            }
        }
        appScope.launch {
            container.realtimeSocketClient.domainEvents.collect { domain ->
                container.staffNotificationManager.onRealtimeDomainEvent(domain)
            }
        }
    }
}
