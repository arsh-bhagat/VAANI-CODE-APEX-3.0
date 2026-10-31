package com.bithead.shelter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.*
import androidx.credentials.exceptions.*
import com.google.android.libraries.identity.googleid.*
import com.bithead.shelter.R
import com.bithead.shelter.mesh.*
import com.bithead.shelter.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Local Google profile connection only: no backend session and no ID token persistence. */
@Composable
internal fun GoogleAccountPanel(onContinue: (() -> Unit)? = null) {
    val context = LocalContext.current
    val source = remember { LocalMeshProfileSource(context.applicationContext) }
    val manager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(source.accountEmail()) }
    var busy by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(source) {
        val subscription = source.observeAccountChanges { account = source.accountEmail() }
        onDispose { subscription.close() }
    }
    Panel {
        Heading(stringResource(R.string.account_title))
        Caption(stringResource(R.string.account_intro))
        account?.let { Text(it) }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        diagnostic?.let { Text(stringResource(R.string.account_diagnostic, it), style = MaterialTheme.typography.bodySmall) }
        Button(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                error = null
                diagnostic = null
                try {
                    if (account != null) {
                        // Stop sharing and clear local identity even if the provider is unavailable.
                        source.clearAccountProfile()
                        try { manager.clearCredentialState(ClearCredentialStateRequest()) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = R.string.account_signout_failed }
                    } else {
                        val clientId = context.getString(R.string.google_web_client_id).trim()
                        if (!clientId.endsWith(".apps.googleusercontent.com")) {
                            error = R.string.account_config_error
                            return@launch
                        }
                        val option = GetSignInWithGoogleOption.Builder(clientId).build()
                        val result = manager.getCredential(context, GetCredentialRequest.Builder()
                            .addCredentialOption(option).build()).credential
                        if (result !is CustomCredential || result.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            error = R.string.account_invalid
                            return@launch
                        }
                        val google = GoogleIdTokenCredential.createFrom(result.data)
                        val email = google.id.trim()
                        val name = google.displayName?.trim()?.takeIf { it.isNotEmpty() }
                            ?: email.substringBefore('@')
                        if (!MeshCodec.validEmail(email) || email.isBlank() || !MeshCodec.validName(name.take(48))) {
                            error = R.string.account_invalid
                            return@launch
                        }
                        // Tokens, Google IDs and photos never enter preferences or BLE packets.
                        source.applyAccountProfile(AccountProfileSeed(name, email = email))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: GetCredentialCancellationException) {
                    error = R.string.account_cancelled
                    diagnostic = accountDiagnostic(failure)
                }
                catch (_: NoCredentialException) { error = R.string.account_unavailable }
                catch (_: GetCredentialProviderConfigurationException) { error = R.string.account_provider_error }
                catch (_: GoogleIdTokenParsingException) { error = R.string.account_invalid }
                catch (failure: GetCredentialException) {
                    // Expose only provider error category and numeric status, never credentials or account data.
                    diagnostic = accountDiagnostic(failure)
                    val reason = failure.message.orEmpty().lowercase(java.util.Locale.ROOT)
                    error = when {
                        "28444" in reason || "developer console" in reason || "developer_error" in reason -> R.string.account_oauth_rejected
                        "network" in reason || "connection" in reason -> R.string.account_network_failed
                        else -> R.string.account_failed
                    }
                }
                catch (failure: Exception) {
                    diagnostic = failure.javaClass.simpleName
                    error = R.string.account_failed
                }
                finally { busy = false }
            }
        }) {
            Text(stringResource(if (busy) R.string.account_busy else if (account == null) R.string.account_sign_in else R.string.account_sign_out))
        }
        onContinue?.let { next ->
            TextButton(enabled = !busy, onClick = next) {
                Text(stringResource(if (account == null) R.string.account_skip else android.R.string.ok))
            }
        }
    }
}

/** Numeric status and exception type only; raw provider text may contain personal data. */
internal fun accountDiagnostic(failure: GetCredentialException): String {
    val code = Regex("\\[(\\d{1,6})\\]").find(failure.message.orEmpty())?.groupValues?.get(1)
        ?: Regex("(?:^|: )([0-9]{1,6}):").find(failure.message.orEmpty())?.groupValues?.get(1)
    val type = failure.type.takeLast(100).filter { it.isLetterOrDigit() || it in "._" }
    return listOfNotNull(failure.javaClass.simpleName, code, type.takeIf { it.isNotBlank() }).joinToString(" / ")
}
