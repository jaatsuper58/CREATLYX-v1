package com.chattlyx.feature.settings.devices

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import com.chattlyx.core.common.result.Result
import com.chattlyx.core.designsystem.component.EmptyState
import com.chattlyx.core.designsystem.component.SkeletonRow
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.domain.auth.DeviceInfo
import com.chattlyx.domain.auth.usecases.GetDevicesUseCase
import com.chattlyx.domain.auth.usecases.RevokeDeviceUseCase
import com.chattlyx.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S47 devices state. */
data class DevicesState(
    val loading: Boolean = true,
    val devices: List<DeviceInfo> = emptyList(),
    val pendingRevoke: DeviceInfo? = null,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val revokeDeviceUseCase: RevokeDeviceUseCase,
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(dispatcher) {
            when (val result = getDevicesUseCase()) {
                is Result.Success -> _state.update {
                    it.copy(loading = false, devices = result.value)
                }

                is Result.Failure -> _state.update { it.copy(loading = false) }
            }
        }
    }

    fun requestRevoke(device: DeviceInfo) = _state.update { it.copy(pendingRevoke = device) }

    fun cancelRevoke() = _state.update { it.copy(pendingRevoke = null) }

    fun confirmRevoke() {
        val target = _state.value.pendingRevoke ?: return
        viewModelScope.launch(dispatcher) {
            revokeDeviceUseCase(target.deviceId)
            _state.update { it.copy(pendingRevoke = null) }
            refresh()
        }
    }
}

/** S47: lists linked devices and allows revoking them. */
@Composable
fun DevicesScreen(modifier: Modifier = Modifier, viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DevicesContent(
        state = state,
        onRequestRevoke = viewModel::requestRevoke,
        onCancelRevoke = viewModel::cancelRevoke,
        onConfirmRevoke = viewModel::confirmRevoke,
        modifier = modifier,
    )
}

@Composable
internal fun DevicesContent(
    state: DevicesState,
    onRequestRevoke: (DeviceInfo) -> Unit,
    onCancelRevoke: () -> Unit,
    onConfirmRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_devices_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            when {
                state.loading -> SkeletonRow(modifier = Modifier.fillMaxWidth().height(160.dp))

                state.devices.isEmpty() -> EmptyState(
                    icon = com.chattlyx.core.designsystem.icon.ChattlyxIcons.Settings,
                    title = stringResource(R.string.settings_devices_title),
                    message = stringResource(R.string.settings_devices_empty),
                )

                else -> LazyColumn {
                    items(state.devices, key = { it.deviceId }) { device ->
                        DeviceRow(device = device, onRevoke = { onRequestRevoke(device) })
                        HorizontalDivider()
                    }
                }
            }

            state.pendingRevoke?.let { device ->
                AlertDialog(
                    onDismissRequest = onCancelRevoke,
                    title = { Text(device.name) },
                    text = { Text(stringResource(R.string.settings_devices_revoke_confirm)) },
                    confirmButton = {
                        TextButton(onClick = onConfirmRevoke) {
                            Text(stringResource(R.string.settings_devices_revoke))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onCancelRevoke) {
                            Text(stringResource(R.string.settings_common_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceInfo, onRevoke: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (device.current) {
                    "${device.name} — ${stringResource(R.string.settings_devices_current)}"
                } else {
                    device.name
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.settings_devices_last_seen,
                    formatRelativeTime(device.lastSeenAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!device.current) {
            TextButton(onClick = onRevoke) {
                Text(
                    stringResource(R.string.settings_devices_revoke),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatRelativeTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun DevicesContentPreview() {
    ChattlyxTheme {
        DevicesContent(
            state = DevicesState(
                loading = false,
                devices = listOf(
                    DeviceInfo(1, "Pixel 9 Pro", 0L, System.currentTimeMillis(), current = true),
                    DeviceInfo(2, "Tablet", 0L, System.currentTimeMillis() - 3_600_000, current = false),
                ),
            ),
            onRequestRevoke = {},
            onCancelRevoke = {},
            onConfirmRevoke = {},
        )
    }
}
