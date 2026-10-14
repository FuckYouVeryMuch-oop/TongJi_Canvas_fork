package com.mln.tongji_canvas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mln.tongji_canvas.data.SessionRepository
import com.mln.tongji_canvas.data.SignHistoryStore
import com.mln.tongji_canvas.data.SignSync
import com.mln.tongji_canvas.ui.OAuth2LoginScreen
import com.mln.tongji_canvas.ui.MainScreen
import com.mln.tongji_canvas.ui.ScannerScreen
import com.mln.tongji_canvas.ui.SimpleScannerScreen
import com.mln.tongji_canvas.ui.EnhancedScannerScreen
import com.mln.tongji_canvas.ui.BatchSignScreen
import com.mln.tongji_canvas.ui.SignHistoryScreen
import com.mln.tongji_canvas.ui.SignSyncScreen
import com.mln.tongji_canvas.ui.theme.Canvas_batch_signTheme

class MainActivity : ComponentActivity() {
    private var guideRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_guide", false)) guideRequest++
        enableEdgeToEdge()
        val repository = SessionRepository(this)
        val history = SignHistoryStore(this)
        val sync = SignSync(this)
        setContent {
            Canvas_batch_signTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            repository = repository,
                            sync = sync,
                            guideRequest = guideRequest,
                            onAddUser = { nav.navigate("login") },
                            onScanQr = { selectedUserIds ->
                                // 将选中的用户ID列表编码为URL参数
                                val userIdsParam = selectedUserIds.joinToString(",")
                                nav.navigate("scanner?selectedUsers=" + java.net.URLEncoder.encode(userIdsParam, "UTF-8"))
                            },
                            onOpenHistory = { nav.navigate("history") },
                            onOpenSync = { nav.navigate("sync") },
                            onTestSession = { /* TODO: open test page with cookies */ }
                        )
                    }
                    composable("login") {
                        OAuth2LoginScreen(repository = repository, initialUrl = "https://canvas.tongji.edu.cn/") { nav.popBackStack() }
                    }
                    composable("history") {
                        SignHistoryScreen(store = history, onBack = { nav.popBackStack() })
                    }
                    composable("sync") {
                        SignSyncScreen(repository, sync, onBack = { nav.popBackStack() })
                    }
                    composable("scanner?selectedUsers={selectedUsers}") { backStackEntry ->
                        val selectedUsersParam = backStackEntry.arguments?.getString("selectedUsers")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                        EnhancedScannerScreen { detectedUrl ->
                            nav.navigate("batchsign?url=" + java.net.URLEncoder.encode(detectedUrl, "UTF-8") + 
                                "&selectedUsers=" + java.net.URLEncoder.encode(selectedUsersParam, "UTF-8"))
                        }
                    }
                    composable("scanner") {
                        EnhancedScannerScreen { detectedUrl ->
                            nav.navigate("batchsign?url=" + java.net.URLEncoder.encode(detectedUrl, "UTF-8"))
                        }
                    }
                    composable("batchsign?url={url}&selectedUsers={selectedUsers}") { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                        val selectedUsersParam = backStackEntry.arguments?.getString("selectedUsers")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                        val selectedUserIds = if (selectedUsersParam.isNotEmpty()) {
                            selectedUsersParam.split(",").toSet()
                        } else {
                            emptySet<String>()
                        }
                        BatchSignScreen(repository = repository, sync = sync, targetUrl = url, selectedUserIds = selectedUserIds)
                    }
                    composable("batchsign?url={url}") { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                        BatchSignScreen(repository = repository, sync = sync, targetUrl = url, selectedUserIds = emptySet())
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_guide", false)) guideRequest++
    }
}
