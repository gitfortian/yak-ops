# Realtime Sync deployment

实时同步控制面支持两种 Flink CDC 提交方式：

- `LOCAL`：Yak Ops 与 Flink CDC CLI 位于同一执行环境，直接通过本地 `ProcessBuilder` 调用 `flink-cdc.sh`。
- `SSH`：Yak Ops 可以运行在 Windows 或其他机器，通过本机 OpenSSH 客户端连接 Linux 执行节点，在远端调用 `flink-cdc.sh`。

无论使用哪种提交方式，任务状态、JobId 恢复、Checkpoint、Metrics 和运行诊断仍然通过 `yak.sync.realtime.rest-url` 直接访问 Flink REST API。SSH 不作为 Flink REST 代理。

## LOCAL 模式

默认配置就是 `LOCAL`，现有部署无需调整：

```yaml
yak:
  sync:
    realtime:
      submission-mode: LOCAL
      rest-url: http://127.0.0.1:8081
      flink-home: /opt/flink
      flink-cdc-home: /opt/flink-cdc
      java-home: /usr/lib/jvm/java-17
      work-directory: ./data/realtime-sync
```

`flink-home`、`flink-cdc-home`、`java-home` 都表示 Yak Ops 所在机器的本地路径。

## SSH 模式：Yak Ops 在 Windows，Flink CDC 在 Linux

推荐使用 OpenSSH key 或 ssh-agent，Yak Ops 不保存 SSH 登录密码。

```yaml
yak:
  sync:
    realtime:
      submission-mode: SSH

      # Yak Ops 后端必须可以直接访问这个 Flink REST 地址。
      rest-url: http://10.0.0.20:8081

      # SSH 模式下，这三个路径都是 Linux 执行节点上的绝对路径。
      flink-home: /opt/flink-1.20.5
      flink-cdc-home: /opt/flink-cdc-3.6.0
      java-home: /usr/lib/jvm/java-17

      # 这里只保存脱敏后的提交日志，不保存远端 Pipeline YAML。
      work-directory: ./data/realtime-sync

      ssh:
        # Windows 可显式配置 C:/Windows/System32/OpenSSH/ssh.exe；
        # 如果 ssh 已加入 PATH，保留默认值 ssh 即可。
        executable: C:/Windows/System32/OpenSSH/ssh.exe
        host: 10.0.0.20
        port: 22
        user: flink

        # identity-file 可以省略，此时 OpenSSH 使用默认 key / ssh-agent。
        identity-file: C:/Users/your-user/.ssh/id_ed25519
        known-hosts-file: C:/Users/your-user/.ssh/known_hosts
        strict-host-key-checking: true
        connect-timeout: 5s

        # 这是 Linux 执行节点调用 Flink CDC CLI 时传给 -Drest.address/-Drest.port 的地址。
        # 如果 Linux 上的 Flink REST 只监听 localhost，可以与上面的 rest-url 不同。
        remote-rest-address: 127.0.0.1
        remote-rest-port: 8081
```

典型链路：

```text
Windows / Yak Ops
  ├─ HTTP ───────────────> Flink REST 10.0.0.20:8081
  └─ OpenSSH ────────────> Linux 10.0.0.20
                              └─ /opt/flink-cdc/bin/flink-cdc.sh
                                     └─ Flink cluster
```

## SSH 账号要求

SSH 用户不需要 root 权限，但至少需要：

- 能执行 `${flink-cdc-home}/bin/flink-cdc.sh`。
- 能读取 Flink CDC 的 `lib`/connector 文件和 Flink 安装目录。
- 能在远端 `${TMPDIR:-/tmp}` 创建临时文件。
- 能从 Linux 执行节点访问 `remote-rest-address:remote-rest-port`。

Yak Ops 会使用 `BatchMode=yes`，因此任何需要交互输入密码、验证码或首次确认 host key 的 SSH 登录都会失败。

建议提前在 Yak Ops 运行账号下验证：

```bash
ssh -i ~/.ssh/id_ed25519 flink@10.0.0.20 'test -x /opt/flink-cdc/bin/flink-cdc.sh && echo ok'
```

开启 `strict-host-key-checking: true` 时，应提前维护可信 `known_hosts`。不要为了绕过 host key 问题长期关闭校验。

## Pipeline YAML 安全边界

SSH 模式不会在 Yak Ops 本地创建包含数据源密码的 Pipeline YAML 文件。

提交过程为：

```text
Yak Ops 内存中的 Pipeline YAML
  -> OpenSSH stdin
  -> Linux mktemp 临时文件（umask 077）
  -> flink-cdc.sh
  -> trap 自动删除远端临时文件
```

Yak Ops 本地只保留提交 stdout/stderr，并在落盘前后执行密码脱敏。提交成功、失败、超时或 SSH 中断时都保留本次 deployment 的脱敏提交日志，便于排障。

## 失败语义

OpenSSH exit code `255`、SSH 连接在提交过程中断开、提交超时或线程中断都会被视为“提交结果不确定”。控制面不会自动再次提交，而是交给已有的 runtime identity / JobId 恢复与生命周期对账逻辑确认 Flink 中是否已经存在该 Job。

远端 CLI 明确返回普通非零 exit code 时，按确定失败处理，并保留脱敏后的 CLI 输出。

## 能力接口

`GET /api/v1/realtime-sync/runtime/capabilities` 会额外返回：

```json
{
  "submissionMode": "SSH",
  "submissionEndpoint": "flink@10.0.0.20:22",
  "restTransport": "DIRECT"
}
```

`deployEnabled=true` 表示配置字段完整；发布/启动时还会执行一次 SSH 远端环境探测，确认远端 Flink CDC CLI、Flink Home、Java（如配置）和 `mktemp` 可用。

## 不包含的能力

当前 SSH 模式只解决“远程执行 Flink CDC CLI”。它不包含：

- SSH 隧道代理 Flink REST。
- Runtime Agent / 常驻远端进程。
- SSH 密码托管。
- JobManager/TaskManager 日志文件通过 SSH 下载。

如果 Yak Ops 无法直接访问 Flink REST，应优先通过内网网络、反向代理或安全网关开放 REST 可达性，再考虑额外的 Runtime Agent 方案。
