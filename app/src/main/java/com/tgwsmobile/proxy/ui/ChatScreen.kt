package com.tgwsmobile.proxy.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.tgwsmobile.proxy.ProxySettings
import com.tgwsmobile.proxy.R
import com.tgwsmobile.proxy.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

private const val GPT_URL = "https://chatgpt.com/"
private const val GPT_LOGIN_URL = "https://chatgpt.com/auth/login"
private const val MAX_AUTOSEND_ATTEMPTS = 20
private const val AUTOSEND_RETRY_MS = 1500L
private const val AUTOSEND_INITIAL_DELAY_MS = 2500L

/** Ready-made prompts, shown as chips above the chat. */
private val GPT_PRESET_PROMPTS = listOf(
    "Улучши качество фото",
    "Ответ дай в виде фото на тетрадном листе",
    "Дай только ответы",
    "Дай развёрнутые решения",
    "Реши задачу по шагам и объясни понятно",
    "Проверь решение и укажи ошибки",
    "Объясни тему простыми словами",
    "Перепиши текст аккуратно и грамотно",
)

/**
 * Signals from other tabs (quick card actions) into the ChatGPT WebView.
 * Single-slot events, replaced by the next request.
 */
object GptTabBridge {
    private var reloadAction: (() -> Unit)? = null
    private var loginAction: (() -> Unit)? = null

    fun requestReload() {
        reloadAction?.invoke()
    }

    fun requestLogin() {
        loginAction?.invoke()
    }

    fun onReady(onReload: () -> Unit, onLogin: () -> Unit) {
        reloadAction = onReload
        loginAction = onLogin
    }
}

@Composable
fun ChatScreen(store: SettingsStore) {
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = ProxySettings())
    val scope = rememberCoroutineScope()

    var gearOpen by rememberSaveable { mutableStateOf(false) }
    // The prompt that is re-sent automatically when a photo is attached (GDZ mode).
    var activePrompt by rememberSaveable { mutableStateOf(GPT_PRESET_PROMPTS.first()) }

    // WebView proxy (so ChatGPT can go through the user's own HTTP proxy).
    LaunchedEffect(settings.gptProxyEnabled, settings.gptProxyHost, settings.gptProxyPort) {
        applyGptProxy(settings)
    }

    Box(Modifier.fillMaxSize()) {
        GptWebView(
            settings = settings,
            activePrompt = activePrompt,
            modifier = Modifier.fillMaxSize(),
        )
        // The only control over the chat: gear in the top-right corner. Prompts live inside it.
        IconButton(
            onClick = { gearOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.gpt_gear))
        }
        if (gearOpen) {
            GptGearSheet(
                store = store,
                settings = settings,
                scope = scope,
                activePrompt = activePrompt,
                onSelectPrompt = { activePrompt = it },
                onClose = { gearOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GptWebView(settings: ProxySettings, activePrompt: String, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var pendingUpload by remember {
        mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val callback = pendingUpload
        pendingUpload = null
        callback?.onReceiveValue(uri?.let { arrayOf(it) })
        // Once the photo lands in the composer, auto-send the active prompt (GDZ mode).
        if (uri != null && settings.gptAutoSend) {
            webView?.autoSendAfterUpload(activePrompt)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                with(this.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // Cloudflare on chatgpt.com blocks the WebView user agent ("; wv").
                    // A plain Chrome UA lets the site load.
                    userAgentString = userAgentString.replace("; wv", "")
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // Keep everything in-app; only hand off non-http(s) schemes.
                        val scheme = request.url.scheme
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: android.webkit.WebResourceError,
                    ) {
                        // Only main-frame failures mean the site did not load.
                        if (request.isForMainFrame) loadError = true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        loadError = false
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        view: WebView,
                        callback: android.webkit.ValueCallback<Array<Uri>>,
                        params: FileChooserParams,
                    ): Boolean {
                        pendingUpload = callback
                        filePicker.launch("image/*")
                        return true
                    }
                }
                GptTabBridge.onReady(
                    onReload = { reload() },
                    onLogin = { loadUrl(GPT_LOGIN_URL) },
                )
                loadUrl(GPT_URL)
                webView = this
            }
        },
        modifier = modifier,
    )

    if (loadError) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.Surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.gpt_load_error), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                loadError = false
                webView?.reload()
            }) {
                Text(stringResource(R.string.gpt_reload))
            }
        }
    }
}

/** Injects [prompt] into the ChatGPT composer and presses send, retrying while the page loads. */
private fun WebView.autoSendAfterUpload(prompt: String) {
    val script = "javascript:(function(){var p=${jsString(prompt)}$JS_INJECT_AND_SEND})"
    val handler = Handler(Looper.getMainLooper())
    var attempts = 0
    fun attempt() {
        if (attempts++ >= MAX_AUTOSEND_ATTEMPTS) return
        evaluateJavascript(script) { result ->
            // The composer may not exist yet right after the upload; retry until it does.
            if (result?.contains("no-input") == true) {
                handler.postDelayed({ attempt() }, AUTOSEND_RETRY_MS)
            }
        }
    }
    // Give the page time to finish uploading the photo before the first attempt.
    handler.postDelayed({ attempt() }, AUTOSEND_INITIAL_DELAY_MS)
}

private const val JS_INJECT_AND_SEND = """
  var t = document.querySelector('div#prompt-textarea, div[contenteditable="true"], textarea[data-id="root"], textarea');
  if (!t) { return 'no-input'; }
  if (t.tagName === 'TEXTAREA') {
    var s = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
    s.call(t, p);
    t.dispatchEvent(new Event('input', {bubbles: true}));
  } else {
    t.focus();
    document.execCommand('insertText', false, p);
    t.dispatchEvent(new InputEvent('input', {bubbles: true, data: p}));
  }
  var btn = document.querySelector('[data-testid="send-button"], button[aria-label*="Send"], button[aria-label*="Отправить"]');
  if (btn && !btn.disabled) { btn.click(); return 'ok'; }
  t.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true}));
  return 'ok';
"""

private fun jsString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }
    append('"')
}

@Composable
private fun GptGearSheet(
    store: SettingsStore,
    settings: ProxySettings,
    scope: CoroutineScope,
    activePrompt: String,
    onSelectPrompt: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newPrompt by rememberSaveable { mutableStateOf("") }
    var proxyHost by rememberSaveable(settings.gptProxyHost) { mutableStateOf(settings.gptProxyHost) }
    var proxyPort by rememberSaveable(settings.gptProxyPort) { mutableStateOf(settings.gptProxyPort) }

    fun save(transform: (ProxySettings) -> ProxySettings) {
        scope.launch { store.update(transform) }
    }

    Column(
        modifier
            .background(Palette.Surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gpt_gear),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.gpt_close))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gpt_auto_send),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.gptAutoSend,
                onCheckedChange = { checked -> save { it.copy(gptAutoSend = checked) } },
            )
        }
        Text(stringResource(R.string.gpt_auto_send_hint), style = MaterialTheme.typography.bodySmall)

        Text(stringResource(R.string.gpt_prompts_section), style = MaterialTheme.typography.titleMedium)
        (GPT_PRESET_PROMPTS + settings.gptCustomPrompts).forEach { prompt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (prompt == activePrompt) Palette.Online else Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (prompt in settings.gptCustomPrompts) {
                    IconButton(onClick = {
                        save { it.copy(gptCustomPrompts = it.gptCustomPrompts - prompt) }
                    }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.gpt_delete_prompt),
                        )
                    }
                }
                TextButton(onClick = { onSelectPrompt(prompt) }) {
                    Text(
                        stringResource(
                            if (prompt == activePrompt) R.string.gpt_prompt_selected else R.string.gpt_prompt_select
                        )
                    )
                }
            }
        }

        OutlinedTextField(
            value = newPrompt,
            onValueChange = { newPrompt = it },
            label = { Text(stringResource(R.string.gpt_custom_prompt)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = newPrompt.isNotBlank(),
            onClick = {
                val prompt = newPrompt.trim()
                save { it.copy(gptCustomPrompts = it.gptCustomPrompts + prompt) }
                newPrompt = ""
            },
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(" " + stringResource(R.string.gpt_add_prompt))
        }

        settings.gptCustomPrompts.forEach { prompt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                IconButton(onClick = {
                    save { it.copy(gptCustomPrompts = it.gptCustomPrompts - prompt) }
                }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.gpt_delete_prompt),
                    )
                }
            }
        }

        Text(stringResource(R.string.gpt_proxy_section), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.gpt_proxy_enabled),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.gptProxyEnabled,
                onCheckedChange = { checked -> save { it.copy(gptProxyEnabled = checked) } },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = proxyHost,
                onValueChange = { proxyHost = it },
                label = { Text(stringResource(R.string.gpt_proxy_host)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = proxyPort,
                onValueChange = { proxyPort = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.gpt_proxy_port)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            enabled = settings.gptProxyHost != proxyHost.trim() || settings.gptProxyPort != proxyPort.trim(),
            onClick = {
                save { it.copy(gptProxyHost = proxyHost.trim(), gptProxyPort = proxyPort.trim()) }
            },
        ) { Text(stringResource(R.string.gpt_proxy_save)) }
        Text(stringResource(R.string.gpt_proxy_hint), style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = { GptTabBridge.requestLogin(); onClose() }) {
            Text(stringResource(R.string.gpt_action_login))
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** Applies (or clears) a WebView-wide HTTP proxy for the ChatGPT tab. */
private fun applyGptProxy(settings: ProxySettings) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
    val executor = Executor { it.run() }
    if (settings.gptProxyEnabled && settings.gptProxyHost.isNotBlank()) {
        val port = settings.gptProxyPort.toIntOrNull() ?: return
        val config = ProxyConfig.Builder()
            .addProxyRule("${settings.gptProxyHost.trim()}:$port")
            .build()
        ProxyController.getInstance().setProxyOverride(config, executor) {}
    } else {
        ProxyController.getInstance().clearProxyOverride(executor) {}
    }
}
