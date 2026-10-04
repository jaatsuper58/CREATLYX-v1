package com.chattlyx.feature.settings.danger

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.theme.ChattlyxTheme
import com.chattlyx.domain.auth.usecases.DeleteAccountUseCase
import com.chattlyx.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S50 deletion state. */
data class DeleteAccountState(
    val acknowledged: Boolean = false,
    val deleting: Boolean = false,
    val errorVisible: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val deleteAccountUseCase: DeleteAccountUseCase,
    @Dispatcher(ChattlyxDispatcher.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountState())
    val state: StateFlow<DeleteAccountState> = _state.asStateFlow()

    fun onAcknowledgedChange(value: Boolean) = _state.update { it.copy(acknowledged = value) }

    /** AUTH-10: irreversible — UI double-confirms before calling this. */
    fun delete() {
        if (_state.value.deleting || !_state.value.acknowledged) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(deleting = true, errorVisible = false) }
            when (deleteAccountUseCase()) {
                is Result.Success -> _state.update { it.copy(deleting = false, deleted = true) }
                is Result.Failure -> _state.update { it.copy(deleting = false, errorVisible = true) }
            }
        }
    }
}

/** S50: explicit, confirmed account deletion (AUTH-10). */
@Composable
fun DeleteAccountScreen(
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.deleted) {
        if (state.deleted) onAccountDeleted()
    }

    DeleteAccountContent(
        state = state,
        onAcknowledgedChange = viewModel::onAcknowledgedChange,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
internal fun DeleteAccountContent(
    state: DeleteAccountState,
    onAcknowledgedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_delete_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.settings_delete_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.acknowledged,
                    onCheckedChange = onAcknowledgedChange,
                )
                Text(
                    text = stringResource(R.string.settings_delete_confirm_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.errorVisible.let { visible ->
                if (visible) {
                    Text(
                        text = stringResource(R.string.settings_delete_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            ChattlyxButton(
                text = stringResource(R.string.settings_delete_button),
                onClick = onDelete,
                enabled = state.acknowledged && !state.deleting,
                loading = state.deleting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun DeleteAccountContentPreview() {
    ChattlyxTheme {
        DeleteAccountContent(
            state = DeleteAccountState(acknowledged = true),
            onAcknowledgedChange = {},
            onDelete = {},
        )
    }
}
