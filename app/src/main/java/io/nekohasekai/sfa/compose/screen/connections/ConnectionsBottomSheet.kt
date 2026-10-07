package io.nekohasekai.sfa.compose.screen.connections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.model.Connection
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsBottomSheet(serviceStatus: Status, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var contentReady by remember { mutableStateOf(false) }

    LaunchedEffect(sheetState) {
        snapshotFlow {
            sheetState.currentValue == SheetValue.Expanded && !sheetState.isAnimationRunning
        }.first { it }
        // Let the final animation frame draw before starting the first snapshot and rows.
        withFrameNanos { }
        contentReady = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // Keep the same measured height before and after the list arrives.
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            if (contentReady) {
                ConnectionsSheetContent(serviceStatus)
            } else {
                Text(
                    text = stringResource(R.string.title_connections),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ConnectionsSheetContent(serviceStatus: Status) {
    val connectionsViewModel: ConnectionsViewModel = viewModel()
    // Collect here so connection updates do not recompose the whole activity/dashboard.
    val uiState by connectionsViewModel.uiState.collectAsState()
    var selectedConnectionId by remember { mutableStateOf<String?>(null) }
    val selectedConnection = selectedConnectionId?.let { id -> uiState.allConnections.find { it.id == id } }
    var cachedConnection by remember { mutableStateOf<Connection?>(null) }
    if (selectedConnection != null) {
        cachedConnection = selectedConnection
    } else if (selectedConnectionId != null && cachedConnection?.isActive == true) {
        cachedConnection = cachedConnection?.copy(closedAt = System.currentTimeMillis())
    }
    val displayConnection = if (selectedConnectionId != null) cachedConnection else null

    DisposableEffect(connectionsViewModel) {
        connectionsViewModel.setVisible(true)
        onDispose { connectionsViewModel.setVisible(false) }
    }
    LaunchedEffect(connectionsViewModel, serviceStatus) {
        connectionsViewModel.updateServiceStatus(serviceStatus)
    }
    BackHandler(enabled = selectedConnectionId != null) {
        selectedConnectionId = null
    }

    if (displayConnection != null) {
        ConnectionDetailsScreen(
            connection = displayConnection,
            onBack = { selectedConnectionId = null },
            onClose = { selectedConnectionId?.let { connectionsViewModel.closeConnection(it) } },
            asSheet = true,
        )
    } else {
        ConnectionsPage(
            serviceStatus = serviceStatus,
            viewModel = connectionsViewModel,
            asSheet = true,
            showTitle = true,
            manageVisibility = false,
            onConnectionClick = { selectedConnectionId = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
