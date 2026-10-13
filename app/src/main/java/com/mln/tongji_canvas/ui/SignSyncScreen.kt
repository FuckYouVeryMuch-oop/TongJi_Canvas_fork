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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
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
        message = if (ok) null else "网络不可用，显示上次同步的结果"
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
            Text("同步的签到结果", style = MaterialTheme.typography.titleLarge)
            Text("开启后，本机成功签到或收到“已签到过”时会上报结果；打开应用可查看本人账号的同步结果。结果由应用上报，确认时间不等于 Canvas 实际签到时间。")
            Text("Canvas session 相当于登录凭证，只与互相信任的人分享。关闭同步会停止上传和读取、清除待上传内容；服务器已保存的结果不会立即删除。")
            Text("选择本人账号", style = MaterialTheme.typography.titleMedium)
            accounts.forEach { account ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = account.id == ownId, onClick = {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("开启同步（默认关闭）")
                Switch(checked = enabled, enabled = !busy && ownId != null, onCheckedChange = { value ->
                    scope.launch {
                        sync.setEnabled(value)
                        enabled = value
                        if (value) refresh() else { events = emptyList(); message = null }
                    }
                })
            }
            if (enabled && ownId != null) {
                Button(onClick = { scope.launch { refresh() } }, enabled = !busy) { Text("刷新结果") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (lastSync > 0L) Text("上次同步：${formatTime(lastSync)}")
                if (events.isEmpty()) Text("暂无同步结果")
                events.forEach { event ->
                    Text("课程 ${event.courseCode}：已签到 · 确认时间 ${formatTime(event.confirmedAt)}")
                }
            }
        }
    }
}

private fun formatTime(time: Long): String = DateFormat.getDateTimeInstance().format(Date(time))
