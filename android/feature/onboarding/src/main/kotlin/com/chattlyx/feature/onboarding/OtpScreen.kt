package com.chattlyx.feature.onboarding

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chattlyx.core.designsystem.component.ChattlyxButton
import com.chattlyx.core.designsystem.component.ChattlyxButtonVariant
import com.chattlyx.core.designsystem.component.OtpInputField
import com.chattlyx.core.designsystem.theme.ChattlyxTheme

/** AUTH-03: OTP entry with resend back-off and SMS auto-read (GMS-only). */
@Composable
fun OtpScreen(
    e164: String,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OtpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // MASVS-STORAGE (Phase 7): keep the one-time code out of screenshots,
    // recents snapshots and screen recordings while it is on screen.
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestOtp(e164)
    }

    // SMS Retriever: best-effort, silently disabled without GMS.
    val smsRetriever = remember { SmsOtpRetriever(context.applicationContext) }
    DisposableEffect(Unit) {
        smsRetriever.start { code -> viewModel.onCodeChange(code) }
        onDispose { smsRetriever.stop() }
    }

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }

    OtpContent(
        e164 = e164,
        state = state,
        onCodeChange = viewModel::onCodeChange,
        onVerify = { viewModel.verify(e164, android.os.Build.MODEL) },
        onResend = { viewModel.resend(e164) },
        modifier = modifier,
    )
}

@Composable
internal fun OtpContent(
    e164: String,
    state: OtpState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_otp_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.onboarding_otp_body, e164),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            OtpInputField(
                otp = state.code,
                onOtpChange = onCodeChange,
                onComplete = { onVerify() },
                enabled = !state.verifying,
                error = state.errorMessageRes != null,
            )

            state.errorMessageRes?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.resendAvailableInSeconds > 0) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_otp_resend_in,
                            state.resendAvailableInSeconds,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = onResend, enabled = !state.requestingOtp) {
                        Text(stringResource(R.string.onboarding_otp_resend_now))
                    }
                }
                if (state.codeExpiresInSeconds in 1..300) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_otp_expires_in,
                            state.codeExpiresInSeconds,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            ChattlyxButton(
                text = stringResource(R.string.onboarding_verify),
                onClick = onVerify,
                variant = ChattlyxButtonVariant.FILLED,
                enabled = state.code.length == 6,
                loading = state.verifying,
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
private fun OtpContentPreview() {
    ChattlyxTheme {
        OtpContent(
            e164 = "+91 98765 43210",
            state = OtpState(code = "123", resendAvailableInSeconds = 42),
            onCodeChange = {},
            onVerify = {},
            onResend = {},
        )
    }
}
