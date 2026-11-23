package com.tgwsmobile.proxy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgwsmobile.proxy.PortHealth
import com.tgwsmobile.proxy.ProxyJournal
import com.tgwsmobile.proxy.ProxyService
import com.tgwsmobile.proxy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diagnostics: live state, traffic counters and the last 24 hours of problems from the journal. */
@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val running by ProxyService.isRunning.collectAsStateWithLifecycle()
    val starting by ProxyService.isStarting.collectAsStateWithLifecycle()
    val health by ProxyService.portHealth.collectAsStateWithLifecycle()
    val stats by ProxyService.stats.collectAsStateWithLifecycle()
    val error by ProxyService.lastError.collectAsStateWithLifecycle()

    var refreshKey by remember { mutableIntStateOf(0) }
    var problems by remember { mutableStateOf<List<ProxyJournal.Problem>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey, running, health) {
        problems = withContext(Dispatchers.IO) { ProxyJournal.recentProblems(context) }
        loaded = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.nav_diagnostics), style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)

        SurfaceCard {
            KeyValueRow(
                stringResource(R.string.diag_state),
                stringResource(
                    when {
                        starting -> R.string.status_connecting
                        running && health == PortHealth.Listening -> R.string.status_online
                        running -> R.string.diag_port_unknown
                        else -> R.string.status_off
                    }
                ),
            )
            KeyValueRow(
                stringResource(R.string.diag_port),
                stringResource(
                    when (health) {
                        PortHealth.Listening -> R.string.diag_port_ok
                        PortHealth.Unreachable -> R.string.diag_port_bad
                        PortHealth.Unknown -> R.string.diag_port_unknown
                    }
                ),
            )
            KeyValueRow(stringResource(R.string.diag_active), stats.active.toString())
            KeyValueRow(
                stringResource(R.string.traffic_title),
                stringResource(R.string.traffic_up_down, formatBytes(stats.bytesUp), formatBytes(stats.bytesDown)),
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Error)
            }
        }

        SurfaceCard {
            Text(stringResource(R.string.diag_journal), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            PressablePill(
                text = stringResource(R.string.action_refresh),
                onClick = { refreshKey++ },
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            val list = problems
            when {
                !loaded -> Text(stringResource(R.string.diag_loading), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                list == null -> Text(stringResource(R.string.diag_no_journal), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                list.isEmpty() -> Text(stringResource(R.string.diag_no_problems), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                else -> list.forEachIndexed { index, problem ->
                    ProblemRow(problem, index)
                }
            }
        }

        SurfaceCard {
            Text(stringResource(R.string.diag_share_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            PressablePill(
                text = stringResource(R.string.action_share_journal),
                onClick = { ProxyJournal.share(context) },
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProblemRow(problem: ProxyJournal.Problem, index: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(problem) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(androidx.compose.animation.core.tween(Motion.BASE, delayMillis = index * Motion.STAGGER / 2)) +
            expandVertically(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${problem.lastSeen}  ${problem.level}${if (problem.count > 1) "  ×${problem.count}" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (problem.level == "ERROR") Palette.Error else Palette.Connecting,
            )
            Text(problem.message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
        }
    }
}
