package com.mln.tongji_canvas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val tourDescriptions = listOf(
    "剪贴板导入" to "从剪贴板导入朋友分享的账号，或之前备份的账号与课程分组。应用会自动读取剪贴板",
    "分享与备份" to "只复制本人账号给朋友，或备份所有账号和课程分组。复制到剪贴板中",
    "签到历史" to "查看你每次扫码签到的结果。",
    "同步签到" to "开启同步并查看朋友帮你签到的结果。",
    "关于" to "查看应用说明，也可以重新打开新手引导。如果你觉得本项目有用，别忘了给我们点个star！"
)

@Composable
fun ToolbarTourOverlay(target: Rect, index: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    BackHandler(onBack = onSkip)
    val density = LocalDensity.current
    val paddingPx = with(density) { 5.dp.toPx() }
    val cardTop = with(density) { (target.bottom + paddingPx + 16.dp.toPx()).toDp() }
    val (title, description) = tourDescriptions[index]
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        ) {
            drawRect(Color.Black.copy(alpha = 0.76f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(target.left - paddingPx, target.top - paddingPx),
                size = Size(target.width + paddingPx * 2, target.height + paddingPx * 2),
                cornerRadius = CornerRadius(14.dp.toPx()),
                blendMode = BlendMode.Clear
            )
        }
        Box(Modifier.matchParentSize().clickable(onClick = {}))
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = cardTop).padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("顶部按钮 ${index + 1}/5 · $title", style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSkip) { Text("跳过按钮介绍") }
                    Button(onClick = onNext) { Text(if (index == 4) "查看操作步骤" else "下一个") }
                }
            }
        }
    }
}
