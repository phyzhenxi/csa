# 快速启动（one-click gateway）

本仓库根目录提供本地运维脚本，一键启动 Java 网关（`csa.client.GatewayMain`）。

## 前置条件

| 项 | 路径 / 值 |
|----|-----------|
| JDK 21 | `E:\tools\jdk-21.0.12.1+1` |
| MySQL 驱动 | `E:\tools\jars\mysql-connector-j.jar` |
| 已编译类 | `java\out\`（含 `csa.client.GatewayMain`） |
| DB 配置 | `config\db.local.json`（可选；缺省见脚本内默认值） |

数据库默认（亦可写入 `config\db.local.json`）：

- host `192.168.2.100` · port `3306` · user `csa` · password `0OKM)okm123` · db `csa`
- **密码是本地运维工具明文，请按需在 `start.bat` / `start.ps1` 或 `config\db.local.json` 中修改。**

## 1. 检查环境

```powershell
cd E:\REA\csa-rea-artifacts
powershell -NoProfile -ExecutionPolicy Bypass -File .\check-env.ps1
```

期望：java / jar / db.local.json / 端口 1234 空闲 / DB 可达 均为 `[ OK ]`。

## 2. 启动网关

```bat
:: cmd：默认端口 1234，或 start.bat 2222 指定端口
E:\REA\csa-rea-artifacts\start.bat
```

```powershell
# PowerShell：可选 -Port
cd E:\REA\csa-rea-artifacts
.\start.ps1              # 默认 1234
.\start.ps1 -Port 2222
```

启动脚本会：

1. 若存在则设置 `JAVA_HOME=E:\tools\jdk-21.0.12.1+1`
2. 优先读取 `config\db.local.json`，否则使用默认 `CSA_DB_*`
3. classpath = `java\out` + `E:\tools\jars\mysql-connector-j.jar`
4. 运行 `csa.client.GatewayMain <port>`（监听 `127.0.0.1`）

日志出现 `LssGateway listening on 127.0.0.1:1234` 即成功。

## 3. 停止网关

```bat
E:\REA\csa-rea-artifacts\stop.bat
```

按命令行匹配 `GatewayMain` 的 `java.exe` 进程并 `taskkill`。

## 4. 用 SimClient 自测

另开终端（无需真实客户端）：

```powershell
cd E:\REA\csa-rea-artifacts\java
# 协议冒烟（登录 → 角色 → 移动/对话/战斗）
& "E:\tools\jdk-21.0.12.1+1\bin\java.exe" -cp "out;E:\tools\jars\mysql-connector-j.jar" csa.client.SimClient 127.0.0.1 1234

# 四场景联调（正确/错误密码 / 未注册 / 坏 MAC）
& "E:\tools\jdk-21.0.12.1+1\bin\java.exe" -cp "out;E:\tools\jars\mysql-connector-j.jar" csa.client.SimScenarios 127.0.0.1 1234
```

期望 `SIM SCENARIOS PASS`（见 `docs/integration-smoke.md`）。

仅测协议、不连数据库时，启动前设置：

```powershell
$env:CSA_DB_DISABLE = "1"
.\start.ps1
```

## 5. 客户端接入（GFIP → 127.0.0.1）

客户端目录：`E:\REA\csa\嘟嘟石器客户端`  
外挂启动器：`ASSA外挂\assa.exe`（配置 `SA80CFG.INI`）

编辑 `ASSA外挂\SA80CFG.INI`：

```ini
[GFIP]
count=1
D1=127.0.0.1
```

- `[登陆数据]` 的 D1/D2/D3 是线路名，**服务器 IP 写在 `[GFIP]` 的 D1**。
- 端口默认 **1234**（与 `start.bat` / `start.ps1` 默认一致）。

然后运行 `assa.exe` 或 `sa_8001.exe` 连接本地网关。  
完整对帧调试、hosts 劫持等见 [`client-integration-guide.md`](client-integration-guide.md)。

## 6. 脚本一览

| 文件 | 作用 |
|------|------|
| `start.bat` | cmd 一键启动（可跟端口参数） |
| `start.ps1` | PowerShell 启动（`-Port`） |
| `stop.bat` | 结束 GatewayMain 进程 |
| `check-env.ps1` | 环境 / 端口 / DB 连通检查 |
