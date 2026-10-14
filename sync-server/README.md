# 签到同步服务

这是独立于现有应用的本机 HTTP 服务，仅监听 `127.0.0.1:8787`；公网 HTTPS 由 Caddy 提供。部署状态以服务器实时检查为准。

2026-10-14 的实际部署、验证和回滚位置见 [DEPLOYMENT.md](DEPLOYMENT.md)。

部署前生成并安全保存一个不少于 32 字符的随机 `SYNC_HMAC_KEY`，以后重启须保持不变；更换此密钥会使旧事件无法用原 session 读取。主机 systemd 部署使用专用 `tongji-sync` 系统用户，只授予它 `data` 目录写权限。密钥只从环境变量传入，不写入仓库。

2026-10-14 部署时 Docker Hub 的 `python:3.12-alpine` 元数据请求超时。服务器已有 Python 3.12.3，因此改用仓库内的 `tongji-sign-sync.service` 运行同一份 `server.py`，以专用用户、128 MiB 内存上限和本机监听隔离服务；没有安装第三方 Python 包。`compose.yaml` 仍保留供镜像源恢复后使用，并显式设置容器内监听地址。
systemd 校验提示通用的 `nobody` 用户不适合长期服务，已改为专用 `tongji-sync` 用户。若将来切回 Docker Compose，需把数据目录所有者改为容器使用的 UID/GID。

Caddy 可在现有 `:443` 站点中添加 `handle_path /sign-sync/* { reverse_proxy 127.0.0.1:8787 }`，并保留现有 DeepTutor 的兜底路由。部署时先备份原配置、运行 `caddy validate`，再分别检查同步接口和 DeepTutor 原路径。

本地接口测试：`python test_server.py`。服务收到 session 后只存 HMAC 摘要；事件保留 30 天。`ALREADY_SIGNED` 的时间是客户端确认时间，不代表 Canvas 实际签到时间。
