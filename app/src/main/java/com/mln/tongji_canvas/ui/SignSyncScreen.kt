package com.mln.tongji_canvas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mln.tongji_canvas.data.CredentialVerifier
import com.mln.tongji_canvas.data.SessionRepository
import com.mln.tongji_canvas.data.SignSync
import com.mln.tongji_canvas.data.SyncedSignEvent
import com.mln.tongji_canvas.data.UserSession
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignSyncScreen(repository: SessionRepository, sync: SignSync, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<UserSession>>(emptyList()) }
    var ownId by remember { mutableStateOf(sync.ownAccountId()) }
    var enabled by remember { mutableStateOf(sync.enabled()) }
    var events by remember { mutableStateOf<List<SyncedSignEvent>>(emptyList()) }
    var lastSync by remember { mutableStateOf(sync.lastSyncedAt()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun refresh() {
        if (!enabled) return
        val own = accounts.firstOrNull { it.id == ownId } ?: return
        busy = true
        sync.flush()
        val ok = sync.refresh(own)
        events = sync.cachedEvents()
        lastSync = sync.lastSyncedAt()
        message = if (ok) null else "暂时无法从服务器刷新，显示上次读取的结果"
        busy = false
    }

    LaunchedEffect(Unit) {
        accounts = repository.getAllSessions()
        if (ownId != null && accounts.none { it.id == ownId }) {
            sync.setOwnAccount(null)
            ownId = null
        }
        events = sync.cachedEvents()
        if (enabled && ownId != null) refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { refresh() } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("同步签到") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("返回") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("开启同步", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (enabled) "已开启" else "未开启（默认关闭）")
                        Switch(checked = enabled, enabled = !busy, onCheckedChange = { value ->
                            if (value && ownId == null) {
                                message = "请先在下方选择本人账号，再开启同步"
                            } else {
                                scope.launch {
                                    sync.setEnabled(value)
                                    enabled = value
                                    if (value) refresh() else { events = emptyList(); message = null }
                                }
                            }
                        })
                    }
                    if (ownId == null) Text("先在下方选择本人账号，再打开开关。")
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("别人帮我签到", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (enabled && ownId != null) {
                                Button(onClick = { scope.launch { refresh() } }, enabled = !busy) {
                                    Text("刷新结果")
                                }
                                if (lastSync > 0L) Text("上次从服务器读取：${formatTime(lastSync)}")
                                if (events.isEmpty()) Text("暂时没有朋友帮你签到的结果")
                                events.forEach { event ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("${event.courseLabel} · 已签到",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary)
                                            Text("确认时间 ${formatTime(event.confirmedAt)}",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } else {
                                Text("开启同步并选择本人账号后，朋友帮你的签到结果会显示在这里。")
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("选择本人账号", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("选中你自己的账号，才会看到朋友为这个账号签到的结果。")
                    if (accounts.isEmpty()) Text("还没有账号，请先返回首页添加。")
                    accounts.forEach { account ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = account.id == ownId, enabled = !busy, onClick = {
                                scope.launch {
                                    busy = true
                                    val verified = account.accessToken?.let { CredentialVerifier.verify(it) }
                                    if (verified == null || verified.studentId != account.studentId) {
                                        message = "账号验证失败，请重新导入后再标记本人"
                                    } else {
                                        sync.setOwnAccount(account.id)
                                        ownId = account.id
                                        events = emptyList()
                                        lastSync = 0L
                                        message = null
                                        refresh()
                                    }
                                    busy = false
                                }
                            })
                            Text(account.displayName)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("使用提示", style = MaterialTheme.typography.titleMedium)
                    Text("此页只显示朋友帮你签到的结果；本机给本人签到不会显示。你帮朋友签到后，结果会发给朋友查看。")
                    Text("帮签的朋友未开启同步时，这次结果不会上传；你未开启同步时，也无法读取已上传的结果。")
                    Text("显示的确认时间是应用上报的时间，不一定是 Canvas 中的实际签到时间。")
                    Text("只把 cookies 分享给信任的人，因为它可以用于登录你的 Canvas。你的 session 更换后，持旧 cookies 的朋友需要你重新分享新凭证，才能继续帮签。")
                    Text("关闭同步后，本机停止上传和读取，并清除待上传内容；开启同步后，朋友帮签的结果会上传到服务器暂存。服务器保存 session 摘要和签到结果，不保存原始 cookies；查询仅显示近 30 天的结果，过期数据会在后续上报时清理。")
                }
            }
        }
    }
}

private fun formatTime(time: Long): String = DateFormat.getDateTimeInstance().format(Date(time))
