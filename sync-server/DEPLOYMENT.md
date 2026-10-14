# 2026-10-14 ECS 部署记录

目标实例：`i-uf6b6qi46un09uq77ipb`。应用和数据独立放在 `/opt/tongji-sign-sync`；`/opt/deeptutor/data` 未改动。服务代码来自本分支的 `server.py`，没有上传真实 Canvas session。

## 当前运行方式

- 服务器已有 Python 3.12.3，以专用 `tongji-sync` 系统用户运行 `tongji-sign-sync.service`。单元设 `MemoryMax=128M`，仅允许写入自己的 `data` 目录，监听 `127.0.0.1:8787`。运行时测得约 11 MiB 内存。
- `SYNC_HMAC_KEY` 由服务器本地生成，保存在 `/opt/tongji-sign-sync/.env`，权限为 root `0600`；密钥值没有进入仓库或日志。SQLite 数据库位于 `/opt/tongji-sign-sync/data/events.sqlite3`。
- Caddy 的 `/sign-sync/*` 路由转发到同步服务并去掉前缀，其余 HTTPS 请求仍转发到 `127.0.0.1:3782`。变更前的 Caddyfile 备份为 `/etc/caddy/Caddyfile.bak.20261014-081626`，部署配置见 `Caddyfile.deploy`。

## 遇到的问题与处理

1. `docker compose build` 请求 Docker Hub 的 `python:3.12-alpine` 元数据时超时。服务器已有兼容的 Python 3.12，服务代码只使用标准库，因此改为 systemd 运行，无须第三方 Python 包或镜像下载。Compose 文件保留供以后使用。
2. `systemd-analyze verify` 警告通用 `nobody` 用户不适合长期服务。改用专用 `tongji-sync` 用户，并只授予它数据目录写权限；再次校验无警告。
3. 启动命令紧接着请求健康接口时出现一次连接失败；随后确认服务已进入 running 状态并在本机端口监听。后续本机和公网请求均通过。

## 已验证

- `caddy validate` 通过；`caddy`、`tongji-sign-sync.service`、证书续期 timer 均为 active。同步服务只监听 `127.0.0.1:8787`，公网监听仍为原有 80/443。
- 从本机以正常 TLS 校验访问 `https://8.153.174.254/sign-sync/v1/health` 得到 200；无 session 读取得到 400。用合成测试 session 通过公网 HTTPS 上报后，同一 session 读取到 1 条 `SUCCESS`，另一 session 读取到 0 条；重复上报 `ALREADY_SIGNED` 后仍为 1 条，原 `SUCCESS` 不被降级。
- `https://8.153.174.254/` 仍返回 307，DeepTutor 容器仍为 healthy。部署时证书有效期截至 2026-10-19 22:15:04 UTC，续期 timer 为 enabled/active。

未连接 Android 设备，因此尚未验证两台真实手机的扫码与跨设备同步。合成测试事件会按服务端 30 天保留规则过期。

## 回滚

如需撤回路由，可恢复上述 Caddyfile 备份、运行 `caddy validate` 后重载 Caddy，再停用 `tongji-sign-sync.service`。保留 `/opt/tongji-sign-sync/data` 和 `.env` 才能恢复旧事件的读取能力。
