package com.chattlyx.feature.onboarding

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.component.Avatar
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.ChattlyxTextField
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/**
 * AUTH-04 completion: name, optional username/about, avatar pick via Photo
 * Picker (no storage permission needed on any API level).
 */
@Composable
fun ProfileSetupScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let(viewModel::onAvatarPicked)
    }

    LaunchedEffect(state.done) {
        if (state.done) onFinished()
    }

    ProfileSetupContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onAboutChange = viewModel::onAboutChange,
        onPickPhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
internal fun ProfileSetupContent(
    state: ProfileSetupState,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onAboutChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_profile_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.onboarding_profile_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            val pickedImage = remember(state.avatarJpeg) {
                state.avatarJpeg?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
            if (pickedImage != null) {
                Image(
                    bitmap = pickedImage,
                    contentDescription = stringResource(R.string.onboarding_profile_photo_desc),
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                )
            } else {
                Avatar(
                    name = state.displayName.ifBlank { "?" },
                    size = 96.dp,
                )
            }
            TextButton(onClick = onPickPhoto) {
                Text(
                    stringResource(
                        if (state.avatarJpeg == null) {
                            R.string.onboarding_profile_photo_add
                        } else {
                            R.string.onboarding_profile_photo_change
                        },
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            ChattlyxTextField(
                value = state.displayName,
                onValueChange = onNameChange,
                label = stringResource(R.string.onboarding_profile_name_label),
                placeholder = stringResource(R.string.onboarding_profile_name_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            ChattlyxTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = stringResource(R.string.onboarding_profile_username_label),
                placeholder = stringResource(R.string.onboarding_profile_username_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            ChattlyxTextField(
                value = state.about,
                onValueChange = onAboutChange,
                label = stringResource(R.string.onboarding_profile_about_label),
                placeholder = stringResource(R.string.onboarding_profile_about_placeholder),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorRes?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.weight(1f))

            ChattlyxButton(
                text = stringResource(R.string.onboarding_finish),
                onClick = onSave,
                variant = ChattlyxButtonVariant.GRADIENT,
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, fontScale = 1.6f)
@Preview(name = "RTL", showBackground = true, locale = "ar")
@Composable
private fun ProfileSetupContentPreview() {
    ChattlyxTheme {
        ProfileSetupContent(
            state = ProfileSetupState(displayName = "Aarav", username = "aarav_", about = "Hello!"),
            onNameChange = {},
            onUsernameChange = {},
            onAboutChange = {},
            onPickPhoto = {},
            onSave = {},
        )
    }
}
