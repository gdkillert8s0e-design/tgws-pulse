package com.tgwsmobile.proxy.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgwsmobile.proxy.BuildConfig
import com.tgwsmobile.proxy.ProxyLink
import com.tgwsmobile.proxy.ProxyService
import com.tgwsmobile.proxy.ProxySettings
import com.tgwsmobile.proxy.R
import com.tgwsmobile.proxy.SettingsStore
import com.tgwsmobile.proxy.UiSettings
import kotlinx.coroutines.launch

/** Settings: port and pool, Cloudflare routes, autostart, the secret and the about section. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SettingsStore(context) }
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = ProxySettings())
    val ui by store.uiSettings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val secret by store.secret.collectAsStateWithLifecycle(initialValue = "")
    val running by ProxyService.isRunning.collectAsStateWithLifecycle()

    fun save(transform: (ProxySettings) -> ProxySettings) {
        scope.launch { store.update(transform) }
    }
    fun saveUi(transform: (UiSettings) -> UiSettings) {
        scope.launch { store.updateUi(transform) }
    }

    var portText by remember { mutableStateOf(settings.port.toString()) }
    LaunchedEffect(settings.port) { portText = settings.port.toString() }
    fun commitPort() {
        val port = portText.toIntOrNull() ?: settings.port
        save { it.copy(port = port) }
    }

    var poolValue by remember { mutableFloatStateOf(settings.poolSize.toFloat()) }
    LaunchedEffect(settings.poolSize) { poolValue = settings.poolSize.toFloat() }

    var customDomains by remember { mutableStateOf(settings.customCfDomain) }
    LaunchedEffect(settings.customCfDomain) { customDomains = settings.customCfDomain }
    var workerDomain by remember { mutableStateOf(settings.cfWorkerDomain) }
    LaunchedEffect(settings.cfWorkerDomain) { workerDomain = settings.cfWorkerDomain }

    var confirmRegenerate by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)

        SurfaceCard {
            SectionTitle(stringResource(R.string.section_style))

            Text(stringResource(R.string.setting_theme), style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            ThemeGrid(
                selected = ui.theme.toEnumOrNull<ThemeId>() ?: ThemeId.Emerald,
                onSelect = { id -> saveUi { it.copy(theme = id.name) } },
            )

            Text(stringResource(R.string.setting_launch_anim), style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            ChoiceChips(
                options = LaunchAnim.entries,
                selected = ui.launchAnim,
                label = { stringResource(it.labelRes) },
                onSelect = { id -> saveUi { it.copy(launchAnim = id.name) } },
            )

            Text(stringResource(R.string.setting_anim_level), style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            ChoiceChips(
                options = AnimLevel.entries,
                selected = ui.animLevel,
                label = { stringResource(it.labelRes) },
                onSelect = { id -> saveUi { it.copy(animLevel = id.name) } },
            )

            ToggleRow(
                title = stringResource(R.string.setting_bg_effects),
                subtitle = stringResource(R.string.setting_bg_effects_hint),
                checked = ui.bgEffects,
                onChange = { v -> saveUi { it.copy(bgEffects = v) } },
            )

            LabeledSlider(
                label = stringResource(R.string.setting_glow),
                value = ui.glow,
                onCommit = { v -> saveUi { it.copy(glow = v) } },
            )
            LabeledSlider(
                label = stringResource(R.string.setting_opacity),
                value = ui.elementOpacity,
                onCommit = { v -> saveUi { it.copy(elementOpacity = v) } },
            )

            Text(stringResource(R.string.setting_card_style), style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            ChoiceChips(
                options = CardStyle.entries,
                selected = ui.cardStyle,
                label = { stringResource(it.labelRes) },
                onSelect = { id -> saveUi { it.copy(cardStyle = id.name) } },
            )

            Text(stringResource(R.string.setting_button_style), style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            ChoiceChips(
                options = ButtonStyle.entries,
                selected = ui.buttonStyle,
                label = { stringResource(it.labelRes) },
                onSelect = { id -> saveUi { it.copy(buttonStyle = id.name) } },
            )

            PressableTextButton(stringResource(R.string.action_reset_settings)) { confirmReset = true }
        }

        SurfaceCard {
            SectionTitle(stringResource(R.string.section_proxy))
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.setting_port)) },
                supportingText = {
                    Text(stringResource(R.string.setting_port_hint, SettingsStore.MIN_PORT, SettingsStore.MAX_PORT))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitPort() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) commitPort() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setting_pool), style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
                Text("${poolValue.toInt()}", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            }
            Slider(
                value = poolValue,
                onValueChange = { poolValue = it },
                onValueChangeFinished = { save { s -> s.copy(poolSize = poolValue.toInt()) } },
                valueRange = SettingsStore.MIN_POOL.toFloat()..SettingsStore.MAX_POOL.toFloat(),
                steps = SettingsStore.MAX_POOL - SettingsStore.MIN_POOL - 1,
            )
            Text(stringResource(R.string.setting_pool_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            RestartNote(visible = running)
        }

        SurfaceCard {
            SectionTitle(stringResource(R.string.section_cloudflare))
            ToggleRow(
                title = stringResource(R.string.setting_cf_enabled),
                subtitle = stringResource(R.string.setting_cf_enabled_hint),
                checked = settings.cfEnabled,
                onChange = { v -> save { it.copy(cfEnabled = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.setting_custom_cf),
                subtitle = stringResource(R.string.setting_custom_cf_hint),
                checked = settings.customCfEnabled,
                onChange = { v -> save { it.copy(customCfEnabled = v) } },
            )
            AnimatedVisibility(
                visible = settings.customCfEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                OutlinedTextField(
                    value = customDomains,
                    onValueChange = { customDomains = it },
                    label = { Text(stringResource(R.string.setting_custom_cf_domains)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) save { s -> s.copy(customCfDomain = customDomains) } },
                )
            }
            OutlinedTextField(
                value = workerDomain,
                onValueChange = { workerDomain = it },
                label = { Text(stringResource(R.string.setting_worker)) },
                supportingText = { Text(stringResource(R.string.setting_worker_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) save { s -> s.copy(cfWorkerDomain = workerDomain) } },
            )
            RestartNote(visible = running)
        }

        SurfaceCard {
            SectionTitle(stringResource(R.string.section_background))
            ToggleRow(
                title = stringResource(R.string.setting_autostart),
                subtitle = stringResource(R.string.setting_autostart_hint),
                checked = settings.autostart,
                onChange = { v -> save { it.copy(autostart = v) } },
            )
            PressableTextButton(stringResource(R.string.action_battery)) { openBatterySettings(context) }
        }

        SurfaceCard {
            SectionTitle(stringResource(R.string.section_secret))
            KeyValueRow(
                stringResource(R.string.label_secret),
                if (secret.isBlank()) "—" else ProxyLink.mask(secret),
            )
            Text(stringResource(R.string.secret_regenerate_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            PressableTextButton(stringResource(R.string.action_regenerate)) { confirmRegenerate = true }
        }

        SurfaceCard {
            SectionTitle(stringResource(R.string.section_about))
            KeyValueRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
            KeyValueRow(stringResource(R.string.about_core), BuildConfig.CORE_VERSION)
            Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            PressableTextButton(stringResource(R.string.action_source)) { openUrl(context, "https://github.com/Flowseal/tg-ws-proxy") }
            PressableTextButton(stringResource(R.string.action_licenses)) { openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.html") }
            PressableTextButton(stringResource(R.string.action_check_updates)) {
                openUrl(context, "https://github.com/gdkillert8s0e-design/tgws-pulse/releases/latest")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { store.resetAll() }
                }) { Text(stringResource(R.string.action_reset_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text(stringResource(R.string.regenerate_title)) },
            text = { Text(stringResource(R.string.regenerate_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    scope.launch { store.regenerateSecret() }
                }) { Text(stringResource(R.string.action_regenerate)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
}

/** Live colour swatches for one theme; the whole cell acts as the preview. */
@Composable
private fun ThemeGrid(selected: ThemeId, onSelect: (ThemeId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeId.entries.chunked(2).forEach { rowThemes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowThemes.forEach { theme ->
                    val colors = Palette.swatch(theme)
                    val selectedHere = theme == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.SurfaceRaised)
                            .border(
                                width = if (selectedHere) 2.dp else 1.dp,
                                color = if (selectedHere) Palette.Online else Palette.Outline,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { onSelect(theme) }
                            .padding(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            colors.forEach { c -> Box(Modifier.size(14.dp).background(c, RoundedCornerShape(5.dp))) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(theme.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedHere) Palette.Online else Palette.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private inline fun <reified T : Enum<T>> ChoiceChips(
    options: List<T>,
    selected: String,
    label: @Composable (T) -> String,
    noinline onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { option ->
            val chosen = option.name == selected
            Text(
                label(option),
                style = MaterialTheme.typography.bodyMedium,
                color = if (chosen) Palette.Background else Palette.TextPrimary,
                modifier = Modifier
                    .clip(PillShape)
                    .background(if (chosen) Palette.Online else Palette.SurfaceRaised)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        Text("${(local * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
    }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local) },
        valueRange = 0f..1f,
    )
}

@Composable
private fun RestartNote(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Text(
            stringResource(R.string.restart_note),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Connecting,
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PressableTextButton(text: String, onClick: () -> Unit) {
    PressablePill(text = text, onClick = onClick, primary = false, modifier = Modifier.fillMaxWidth())
}
