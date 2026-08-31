package io.nekohasekai.sfa.compose.screen.connections

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Connections
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.ScreenEvent
import io.nekohasekai.sfa.compose.model.Connection
import io.nekohasekai.sfa.compose.model.ConnectionSort
import io.nekohasekai.sfa.compose.model.ConnectionStateFilter
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.ktx.toList
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.CommandTarget
import io.nekohasekai.sfa.utils.RemoteControlManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val CONNECTIONS_UI_UPDATE_INTERVAL_MS = 120L

data class ConnectionsUiState(
    val connections: List<Connection> = emptyList(),
    val allConnections: List<Connection> = emptyList(),
    val isLoading: Boolean = false,
    val stateFilter: ConnectionStateFilter = ConnectionStateFilter.Active,
    val sort: ConnectionSort = ConnectionSort.ByDate,
    val searchText: String = "",
    val isSearchActive: Boolean = false,
)

sealed class ConnectionsEvent : ScreenEvent {
    data class ConnectionClosed(val id: String) : ConnectionsEvent()
    data object AllConnectionsClosed : ConnectionsEvent()
}

class ConnectionsViewModel :
    BaseViewModel<ConnectionsUiState, ConnectionsEvent>(),
    CommandClient.Handler {
    private val commandClient = CommandClient(
        viewModelScope,
        CommandClient.ConnectionType.Connections,
        this,
    )

    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus = _serviceStatus.asStateFlow()
    private var lastServiceStatus: Status = Status.Stopped

    private val _visibleCount = MutableStateFlow(0)

    private var connectionsStore: Connections? = null
    private val connectionsMutex = Mutex()
    private val connectionsGeneration = AtomicLong(0)
    private val connectionsDataVersion = AtomicLong(0)
    private val snapshotUpdateScheduled = AtomicBoolean(false)
    private var connectionSubscribed = false

    override fun createInitialState() = ConnectionsUiState()

    private data class ConnectionState(
        val foreground: Boolean,
        val screenOn: Boolean,
        val visibleCount: Int,
        val status: Status,
        val remoteServerId: Long?,
        val remoteConnected: Boolean,
    )

    init {
        viewModelScope.launch {
            combine(
                AppLifecycleObserver.isForeground,
                AppLifecycleObserver.isScreenOn,
                _visibleCount,
                _serviceStatus,
                combine(
                    RemoteControlManager.remoteServer,
                    RemoteControlManager.isConnected,
                ) { remoteServer, remoteConnected -> remoteServer?.id to remoteConnected },
            ) { foreground, screenOn, visibleCount, status, (remoteServerId, remoteConnected) ->
                ConnectionState(foreground, screenOn, visibleCount, status, remoteServerId, remoteConnected)
            }.collect { state ->
                val serviceReady =
                    if (state.remoteServerId != null) state.remoteConnected else state.status == Status.Started
                val shouldConnect = state.foreground && state.screenOn &&
                    state.visibleCount > 0 && serviceReady
                if (shouldConnect && !connectionSubscribed) {
                    connectionSubscribed = true
                    updateState { copy(isLoading = connections.isEmpty()) }
                    commandClient.connect()
                } else if (!shouldConnect && connectionSubscribed) {
                    connectionSubscribed = false
                    commandClient.disconnect()
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        _visibleCount.value = (_visibleCount.value + if (visible) 1 else -1).coerceAtLeast(0)
    }

    override fun onCleared() {
        super.onCleared()
        commandClient.disconnect()
    }

    private suspend fun handleServiceStatusChange(status: Status) {
        if (RemoteControlManager.remoteServer.value != null) {
            return
        }
        if (status != Status.Started) {
            withContext(Dispatchers.Default) {
                connectionsMutex.withLock {
                    connectionsStore = null
                }
                connectionsGeneration.incrementAndGet()
            }
            updateState {
                copy(connections = emptyList(), allConnections = emptyList(), isLoading = false)
            }
        }
    }

    fun updateServiceStatus(status: Status) {
        if (status == lastServiceStatus) return
        lastServiceStatus = status
        viewModelScope.launch {
            _serviceStatus.emit(status)
            handleServiceStatusChange(status)
        }
    }

    fun setStateFilter(filter: ConnectionStateFilter) {
        updateState { copy(stateFilter = filter) }
        requestConnectionsRefresh()
    }

    fun setSort(sort: ConnectionSort) {
        updateState { copy(sort = sort) }
        requestConnectionsRefresh()
    }

    fun setSearchText(text: String) {
        updateState { copy(searchText = text) }
        requestConnectionsRefresh()
    }

    fun toggleSearch() {
        val newSearchActive = !currentState.isSearchActive
        updateState {
            copy(
                isSearchActive = newSearchActive,
                searchText = if (newSearchActive) searchText else "",
            )
        }
        if (!newSearchActive) {
            requestConnectionsRefresh()
        }
    }

    fun closeConnection(connectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().closeConnection(connectionId)
                withContext(Dispatchers.Main) {
                    sendEvent(ConnectionsEvent.ConnectionClosed(connectionId))
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun closeAllConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CommandTarget.standaloneClient().closeConnections()
                withContext(Dispatchers.Main) {
                    sendEvent(ConnectionsEvent.AllConnectionsClosed)
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isLoading = false) }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isLoading = false) }
        }
    }

    override fun writeConnectionEvents(events: ConnectionEvents) {
        viewModelScope.launch(Dispatchers.Default) {
            val generation = connectionsGeneration.get()
            val applied = connectionsMutex.withLock {
                if (connectionsGeneration.get() != generation) return@withLock false
                if (connectionsStore == null) {
                    connectionsStore = Connections()
                }
                val store = connectionsStore ?: return@withLock false
                store.applyEvents(events)
                connectionsDataVersion.incrementAndGet()
                true
            }
            if (applied) {
                scheduleConnectionSnapshot()
            }
        }
    }

    private fun requestConnectionsRefresh() {
        viewModelScope.launch(Dispatchers.Default) { publishConnectionSnapshot() }
    }

    private fun scheduleConnectionSnapshot() {
        if (!snapshotUpdateScheduled.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.Default) {
            var publishedVersion = -1L
            var publishedGeneration = connectionsGeneration.get()
            try {
                do {
                    delay(CONNECTIONS_UI_UPDATE_INTERVAL_MS)
                    val snapshot = publishConnectionSnapshot() ?: return@launch
                    publishedVersion = snapshot.version
                    publishedGeneration = snapshot.generation
                } while (
                    connectionsGeneration.get() == publishedGeneration &&
                    connectionsDataVersion.get() != publishedVersion
                )
            } finally {
                snapshotUpdateScheduled.set(false)
                if (
                    publishedVersion >= 0L &&
                    connectionsGeneration.get() == publishedGeneration &&
                    connectionsDataVersion.get() != publishedVersion
                ) {
                    scheduleConnectionSnapshot()
                }
            }
        }
    }

    private suspend fun publishConnectionSnapshot(): SnapshotVersion? {
        val generation = connectionsGeneration.get()
        val state = uiState.value
        val snapshot = connectionsMutex.withLock {
            if (connectionsGeneration.get() != generation) return@withLock null
            val store = connectionsStore ?: return@withLock null
            SnapshotVersion(
                generation = generation,
                version = connectionsDataVersion.get(),
                lists = buildConnectionLists(store, state),
            )
        } ?: return null

        if (connectionsGeneration.get() != generation) return null
        withContext(Dispatchers.Main) {
            if (connectionsGeneration.get() == generation) {
                updateState {
                    copy(
                        connections = snapshot.lists.connections,
                        allConnections = snapshot.lists.allConnections,
                        isLoading = false,
                    )
                }
            }
        }
        return snapshot
    }

    private fun buildConnectionLists(
        connections: Connections,
        currentState: ConnectionsUiState,
    ): ConnectionLists {
        connections.filterState(ConnectionStateFilter.All.libboxValue)
        val allConnectionList = connections.iterator().toList()
            .filter { it.outboundType != "dns" }
            .map { Connection.from(it) }

        val connectionList = allConnectionList.asSequence()
            .filter { connection ->
                when (currentState.stateFilter) {
                    ConnectionStateFilter.All -> true
                    ConnectionStateFilter.Active -> connection.isActive
                    ConnectionStateFilter.Closed -> !connection.isActive
                }
            }
            .filter { it.performSearch(currentState.searchText) }
            .toList()
            .let { sortConnections(it, currentState.sort) }

        return ConnectionLists(
            connections = connectionList,
            allConnections = allConnectionList,
        )
    }

    private data class ConnectionLists(
        val connections: List<Connection>,
        val allConnections: List<Connection>,
    )

    private data class SnapshotVersion(
        val generation: Long,
        val version: Long,
        val lists: ConnectionLists,
    )

    private fun sortConnections(connections: List<Connection>, sort: ConnectionSort): List<Connection> = when (sort) {
        ConnectionSort.ByDate -> connections.sortedWith(compareByDescending<Connection> { it.createdAt }.thenByDescending { it.id })
        ConnectionSort.ByTraffic -> connections.sortedWith(
            compareByDescending<Connection> { it.upload + it.download }.thenByDescending { it.id },
        )
        ConnectionSort.ByTrafficTotal -> connections.sortedWith(
            compareByDescending<Connection> { it.uploadTotal + it.downloadTotal }.thenByDescending { it.id },
        )
    }
}
