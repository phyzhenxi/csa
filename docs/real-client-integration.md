# 真实客户端联调准备（E:\REA\csa\客户端）

## 客户端特点

| 项 | 值 |
|----|-----|
| 目录 | `E:\REA\csa\客户端` |
| 主程序 | **`sa_9061.exe`** / `sa_8002.exe`（`Waei SaDeb`） |
| **无 VMProtect** | 无 `Boxc.dll`，可直接跑 |
| 启动 | `无外挂启动.bat` → `sa_9061.exe updated windowmode ...` |
| **服务器地址** | 写死 **`www.longzoro.com`**（当前 DNS 不存在） |
| 信封盐 | 含 `1234567`、base62 / `0-9A-Za-z{}` —— **与我们逆向一致** |

## 连接本地网关

### 1. hosts 劫持（需管理员）

```powershell
# 管理员 PowerShell
Add-Content -Path C:\Windows\System32\drivers\etc\hosts -Value "`n127.0.0.1 www.longzoro.com"
ipconfig /flushdns
```

验证：

```powershell
ping www.longzoro.com   # 应解析到 127.0.0.1
```

### 2. 起网关（端口 1234）

```powershell
cd E:\REA\csa-rea-artifacts
.\start.ps1
```

### 3. 启动客户端

```bat
cd E:\REA\csa\客户端
无外挂启动.bat
```

或：

```bat
sa_9061.exe updated windowmode realbin:138 adrnbin:138 spradrnbin:116 sprbin:116 realtrue:13 adrntrue:5
```

### 4. 账号

数据库 `CSAlogin`：`user1` / `pass1`（已在 `192.168.2.100/csa`）

## 对帧调试

网关打印每帧后，用 `PacketDump.explain` 校准；客户端协议串含 `ClientLogin`/`CharLogin`，与 `lssproto` 一致。

## 注意

- 端口默认 **1234**（`setup.cf`）；若客户端连不上，抓包看实际端口
- `LONGZORO.DLL` 可能还做登录域名校验；hosts 劫持后一般即可
- 恢复：从 hosts 删掉 `127.0.0.1 www.longzoro.com`

---

## 登录后服务器列表

客户端在账号登录后显示「服务器1 / 测试服务器2」。列表由**登录响应下发**，客户端二进制内无写死名称。

网关 `ClientLogin` 成功后追加 `LoginResponses.serverList`：

```text
server1|127.0.0.1|1234;server2|127.0.0.1|1234
```

两项均指向本地网关。若真实客户端解析格式不同，以抓包为准校准 `serverList()`。
