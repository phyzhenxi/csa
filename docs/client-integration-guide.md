# 嘟嘟石器客户端 → 本地网关联调指南

客户端：`E:\REA\csa\嘟嘟石器客户端`  
主程序：`sa_8001.exe`（Boxc.dll / VMProtect）  
外挂启动器：`ASSA外挂\assa.exe`（配置 `SA80CFG.INI`）

## 1. 指定服务器地址

### 方式 A：ASSA 启动器 GFIP（推荐）

编辑 `ASSA外挂\SA80CFG.INI`：

```ini
[GFIP]
count=1
D1=127.0.0.1
```

`[登陆数据]` 的 D1/D2/D3 是线路名，IP 在 `[GFIP]` 的 D1。

### 方式 B：直接跑 `sa_8001.exe`

部分版本支持参数/选服 UI；若登录框可填地址，填 `127.0.0.1`。

### 方式 C：hosts 劫持（若客户端写死域名）

```
127.0.0.1   <客户端内置登录域名>
```

端口默认 **1234**（服务端 `setup.cf` 的 `port=1234`）。

## 2. 启动顺序

```powershell
# 终端 1：网关（监听 1234）
cd E:\REA\csa-rea-artifacts\java
java -cp out csa.client.GatewayMain 1234

# 终端 2：客户端
# ASSA外挂\assa.exe  或  sa_8001.exe
```

## 3. 对帧调试

```java
PacketDump.explain(rawLine);  // 解析 msgId/cmd/args
PacketDump.hex(bytes);        // 十六进制
```

网关日志建议打印每帧 raw，便于和 `PacketDump` 对照。

## 4. 预期第一枪

```text
C→S  ClientLogin <cdkey> <pass> <mac=gmsv-xxx> <servid>
S→C  &;95<encoded ok>;#;
```

若卡住：把网关收到的 raw 帧发我，用 `PacketDump.explain` 校准。

## 5. 已知注意

- MAC 字段需 `gmsv-` 前缀（`freelogincheck.lua`）
- 空格在 checkStringErr 中非法（聊天/账号均受影响）
- 登录响应走 `util_SendMesg` 信封，不是朴素 lssproto 帧