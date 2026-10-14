# feat-rich-sync 构建记录

本分支的交付文件只保留 `output/TongJi_Canvas-feat-rich-sync-universal-debug.apk`。它从 Gradle 生成的 `app-universal-debug.apk` 复制而来；检查 APK 后确认包含 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64`。ABI 专用 APK 不进入 `output`。

## 遇到的问题与处理

- Android Studio 自带的 JDK 25 使 Kotlin 编译目标回退到 22，与项目的 Java 21 目标冲突。改用本机 Temurin JDK 21 编译，保持项目的 Java 目标不变。
- 仓库原先用 `*.jar` 忽略了 Gradle Wrapper JAR，新 worktree 中运行 `gradlew.bat` 会报找不到 `GradleWrapperMain`。已用项目指定的 Gradle 9.0.0 生成 Wrapper JAR，并将其纳入此分支；现在可直接使用 `gradlew.bat`。
- `apksigner.bat` 也需要 Java 环境；验证签名时沿用下面的 `JAVA_HOME`。最终 APK 的 v2 签名验证通过。

在 PowerShell 中，从本 worktree 根目录运行：

```powershell
$env:JAVA_HOME = 'C:\Users\lxc\AppData\Local\Programs\Temurin\jdk-21.0.12.1+1'
$env:ANDROID_HOME = 'C:\Users\lxc\AppData\Local\Android\Sdk'
.\gradlew.bat :app:assembleDebug --offline
New-Item -ItemType Directory -Force output | Out-Null
Copy-Item app\build\outputs\apk\debug\app-universal-debug.apk output\TongJi_Canvas-feat-rich-sync-universal-debug.apk
```

`sync-server/test_server.py` 通过本地 HTTP 接口测试。服务已部署，公网 HTTPS 的合成事件读写与隔离验证见 [部署记录](sync-server/DEPLOYMENT.md)；真实手机扫码与跨设备同步尚未验证。
