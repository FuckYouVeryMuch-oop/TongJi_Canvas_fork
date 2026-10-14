package com.mln.tongji_canvas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingCard(
    hasAccount: Boolean,
    hasOwnAccount: Boolean,
    hasFriend: Boolean,
    hasSelection: Boolean,
    hasScanned: Boolean,
    syncEnabled: Boolean,
    onAdd: () -> Unit,
    onChooseOwn: () -> Unit,
    onShareOwn: () -> Unit,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onSync: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("开始使用", style = MaterialTheme.typography.titleLarge)
            Text("按顺序完成，也可以随时跳过。", style = MaterialTheme.typography.bodySmall)
            Text("${if (hasAccount) "✓" else "1."} 添加本人账号：登录或导入后会验证姓名和学号。")
            TextButton(onClick = onAdd) { Text("添加账号") }
            Text("${if (hasOwnAccount && hasFriend) "✓" else "2."} 互换账号：只分享本人凭证，再导入朋友凭证。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onChooseOwn) { Text("标记本人") }
                TextButton(onClick = onShareOwn, enabled = hasOwnAccount) { Text("分享本人") }
                TextButton(onClick = onImport) { Text("导入朋友") }
            }
            Text("${if (hasScanned) "✓" else "3."} 选择要签到的账号，再扫老师展示的二维码；可一键全选。")
            TextButton(onClick = onScan, enabled = hasSelection) { Text("开始扫码") }
            Text("${if (syncEnabled) "✓" else "4."} 同步可选：标记本人并开启后，下次打开可看帮签结果；关闭时看不到同步结果。")
            TextButton(onClick = onSync) { Text("同步设置") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSkip) { Text("以后再说") }
                Button(onClick = onComplete, enabled = hasAccount && hasOwnAccount && hasFriend && hasScanned) {
                    Text("完成引导")
                }
            }
        }
    }
}
