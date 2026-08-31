package io.nekohasekai.sfa.compose.screen.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sfa.compose.util.AnsiColorUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicLong

@OptIn(FlowPreview::class)
abstract class BaseLogViewModel(initialState: LogUiState = LogUiState()) :
    ViewModel(),
    LogViewerViewModel {
    protected val _uiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    protected val _autoScrollEnabled = MutableStateFlow(true)
    override val isAtBottom: StateFlow<Boolean> = _autoScrollEnabled.asStateFlow()

    protected val _scrollToBottomTrigger = MutableStateFlow(0)
    override val scrollToBottomTrigger: StateFlow<Int> = _scrollToBottomTrigger.asStateFlow()

    protected val _searchQueryInternal = MutableStateFlow("")
    protected val logIdGenerator = AtomicLong(0)
    protected val allLogs = LinkedList<ProcessedLogEntry>()
    private var pausedBeforeSelection = initialState.isPaused

    init {
        viewModelScope.launch {
            _searchQueryInternal
                .debounce(300)
                .distinctUntilChanged()
                .collect {
                    updateDisplayedLogs()
                }
        }
    }

    override fun toggleSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = !it.isSearchActive,
                searchQuery = if (!it.isSearchActive) it.searchQuery else "",
            )
        }
        updateDisplayedLogs()
    }

    override fun toggleOptionsMenu() {
        _uiState.update { it.copy(isOptionsMenuOpen = !it.isOptionsMenuOpen) }
    }

    override fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        _searchQueryInternal.value = query
    }

    open override fun setLogLevel(level: LogLevel) {
        _uiState.update { it.copy(filterLogLevel = level) }
        updateDisplayedLogs()
    }

    override fun setAutoScrollEnabled(enabled: Boolean) {
        _autoScrollEnabled.value = enabled
    }

    override fun scrollToBottom() {
        _autoScrollEnabled.value = true
        _scrollToBottomTrigger.value++
    }

    override fun toggleSelectionMode() {
        _uiState.update {
            if (it.isSelectionMode) {
                it.copy(isSelectionMode = false, selectedLogIndices = emptySet(), isPaused = pausedBeforeSelection)
            } else {
                pausedBeforeSelection = it.isPaused
                it.copy(isSelectionMode = true, isPaused = true)
            }
        }
    }

    override fun toggleLogSelection(index: Int) {
        _uiState.update { state ->
            val newSelection =
                if (state.selectedLogIndices.contains(index)) {
                    state.selectedLogIndices - index
                } else {
                    state.selectedLogIndices + index
                }
            if (newSelection.isEmpty()) {
                state.copy(
                    isSelectionMode = false,
                    selectedLogIndices = emptySet(),
                    isPaused = pausedBeforeSelection,
                )
            } else {
                state.copy(selectedLogIndices = newSelection)
            }
        }
    }

    override fun clearSelection() {
        _uiState.update {
            it.copy(isSelectionMode = false, selectedLogIndices = emptySet(), isPaused = pausedBeforeSelection)
        }
    }

    override fun getSelectedLogsText(): String {
        val state = _uiState.value
        return state.selectedLogIndices
            .sorted()
            .mapNotNull { index ->
                state.logs.getOrNull(index)?.entry?.message?.let { AnsiColorUtils.stripAnsi(it) }
            }
            .joinToString("\n")
    }

    override fun getAllLogsText(): String = _uiState.value.logs.joinToString("\n") { AnsiColorUtils.stripAnsi(it.entry.message) }

    protected fun updateDisplayedLogs() {
        val currentState = _uiState.value
        val levelPriority =
            if (currentState.filterLogLevel != LogLevel.Default) {
                currentState.filterLogLevel.priority
            } else {
                currentState.defaultLogLevel.priority
            }
        val searchQuery = currentState.searchQuery

        val logsToDisplay =
            allLogs.asSequence()
                .filter { log -> log.entry.level.priority <= levelPriority }
                .filter { log ->
                    searchQuery.isEmpty() || log.entry.message.contains(searchQuery, ignoreCase = true)
                }
                .toList()

        val selectionCleared =
            if (_uiState.value.isSelectionMode && _uiState.value.logs != logsToDisplay) {
                emptySet<Int>()
            } else {
                _uiState.value.selectedLogIndices
            }

        _uiState.update { it.copy(logs = logsToDisplay, selectedLogIndices = selectionCleared) }
    }
}
