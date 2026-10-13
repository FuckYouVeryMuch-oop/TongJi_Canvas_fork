# 签到同步服务

这是独立于现有应用的本机 HTTP 服务。容器仅映射到 `127.0.0.1:8787`；公网 HTTPS 由 Caddy 提供。当前分支没有部署或修改线上 Caddy。

部署前生成并安全保存一个不少于 32 字符的随机 `SYNC_HMAC_KEY`，以后重启须保持不变；更换此密钥会使旧事件无法用原 session 读取。准备 `data` 目录并授予容器用户 `65534:65534` 写权限，然后在此目录运行 `docker compose up -d --build`。密钥只从环境变量传入，不写入仓库。

Caddy 可在现有 `:443` 站点中添加 `handle_path /sign-sync/* { reverse_proxy 127.0.0.1:8787 }`，并保留现有 DeepTutor 的兜底路由。部署时先备份原配置、运行 `caddy validate`，再分别检查同步接口和 DeepTutor 原路径。

本地接口测试：`python test_server.py`。服务收到 session 后只存 HMAC 摘要；事件保留 30 天。`ALREADY_SIGNED` 的时间是客户端确认时间，不代表 Canvas 实际签到时间。
