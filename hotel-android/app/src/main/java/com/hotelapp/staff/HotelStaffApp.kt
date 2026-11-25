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
import com.hotelapp.staff.core.notifications.StaffNotificationManager
import com.hotelapp.staff.core.storage.HotelLocalDatabase
import com.hotelapp.staff.core.storage.HotelLocalStore
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
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File

class AppContainer(val application: Application) {
    val serverConfigStore = ServerConfigStore(application)
    val sessionManager = SessionManager()
    val networkMonitor: NetworkMonitor = AndroidNetworkMonitor(application)
    val localStore: HotelLocalStore = HotelLocalDatabase(application)
    val credentialHelper = StaffCredentialHelper(application)
    val staffNotificationManager = StaffNotificationManager(
        context = application,
        serverConfigStore = serverConfigStore,
        sessionProvider = { (sessionManager.authState.value as? AuthState.Authenticated)?.session },
    )

    val offlineOpsCoordinator = OfflineOpsCoordinator(
        localStore = localStore,
        scheduleSyncWorker = { SyncPendingMutationsWorker.schedule(application) },
    )

    private val cookieJar = EncryptedCookieJar(
        storage = FileCookieStorage(File(application.filesDir, "cookies.enc")),
        cipher = AndroidKeystoreCipher(),
    )

    private val refreshClient = NetworkFactory.createRefreshClient(serverConfigStore, cookieJar)
    private val mainClient = NetworkFactory.createMainClient(
        serverConfigStore = serverConfigStore,
        cookieJar = cookieJar,
        sessionManager = sessionManager,
        refreshClient = refreshClient,
    )
    private val wsClient = NetworkFactory.createWebSocketClient(serverConfigStore)

    val realtimeSocketClient = RealtimeSocketClient(
        client = wsClient,
        baseUrlProvider = { serverConfigStore.currentBaseUrl() },
        sessionManager = sessionManager,
        onUnauthorized = { NetworkFactory.refreshAccessTokenSync(refreshClient) },
        isOnlineProvider = { networkMonitor.isOnline.value },
        networkOnlineFlow = networkMonitor.isOnline,
    )

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(NetworkFactory.PLACEHOLDER_BASE_URL)
        .client(mainClient)
        .addConverterFactory(HotelJson.asConverterFactory("application/json".toMediaType()))
        .build()

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

    val serverRepository: ServerRepository = DefaultServerRepository(
        settingsApi = settingsApi,
        serverConfigStore = serverConfigStore,
        sessionManager = sessionManager,
        realtimeSocketClient = realtimeSocketClient,
        onServerChanged = {
            offlineOpsCoordinator.clearAll()
            WidgetRefreshWorker.clearWidget(
                context = application,
                localStore = localStore,
                hotelName = serverConfigStore.config.value.hotelName,
            )
        },
    )
    val authRepository: AuthRepository = DefaultAuthRepository(
        authApi = authApi,
        passkeyApi = passkeyApi,
        serverRepository = serverRepository,
        sessionManager = sessionManager,
        cookieJar = cookieJar,
        realtimeSocketClient = realtimeSocketClient,
        onLoggedOut = {
            offlineOpsCoordinator.clearAll()
            WidgetRefreshWorker.clearWidget(
                context = application,
                localStore = localStore,
                hotelName = serverConfigStore.config.value.hotelName,
            )
        },
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
        SyncPendingMutationsWorker.schedule(application)
    }
}

class HotelStaffApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.staffNotificationManager.ensureChannels()
        WidgetRefreshWorker.schedulePeriodic(this)

        appScope.launch {
            container.offlineOpsCoordinator.refreshPendingCount()
        }
        appScope.launch {
            var wasOnline = container.networkMonitor.isOnline.value
            container.networkMonitor.isOnline.collect { online ->
                if (online && !wasOnline && container.offlineOpsCoordinator.pendingCount.value > 0) {
                    SyncPendingMutationsWorker.schedule(this@HotelStaffApp)
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
