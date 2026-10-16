# P0-a 登录握手契约

来源：`gmsvjt` 符号表 + capstone。相关产物：`data/login-handshake.json`、`java/.../LoginHandshake.java`。

## 总时序

```text
Client                         gmsvjt                              saacjt
  |                              |                                    |
  | 1 ClientLogin cdkey pwd mac servid                               |
  |----------------------------->|                                    |
  |                              |-- checkStringErr(cdkey/pwd)        |
  |                              |-- CONNECT_setCdkey/Passwd/MAC/...  |
  |                              |-- CONNECT_setState(2)              |
  |                              |-- FreeLoginCheck(fd) via Lua       |
  |                              |                                    |
  | 2 ClientLogin "ok"/错误文案  |                                    |
  |<-----------------------------|                                    |
  |                              |                                    |
  | 3 CharList                   |                                    |
  |----------------------------->|                                    |
  |                              |-- ACCharLoad / 查角色              |
  | 4 CharList 角色表或 failed   |                                    |
  |<-----------------------------|                                    |
  |                              |                                    |
  | 5 CharLogin 角色名           |                                    |
  |----------------------------->|                                    |
  |                              |-- ACCharLogin_send ---------------->|
  |                              |<-- ACCharLogin_recv ----------------|
  | 6 CharLogin ok / failed      |                                    |
  |<-----------------------------|                                    |
```

gmsv↔saac 建链时另有 **`ACServerLogin`**（见下）。

---

## 1. ClientLogin（客户端 → gmsv）

**符号**：`lssproto_ClientLogin_recv` @ `0x8094200`（641B）

### 参数（线序，均为转义字符串；servid 为 base62 整数）

| # | 名 | 类型 | 存储 |
|---|-----|------|------|
| 1 | cdkey / 账号 | string | `CONNECT_setCdkey` |
| 2 | password | string | `CONNECT_setPasswd`（连接槽 `+0x58`，长 `0x21`） |
| 3 | mac / 版本令牌 | string | `CONNECT_setMAC` |
| 4 | servid | int (base62) | `CONNECT_setServid` |
| 5 | ctype | int | `CONNECT_setCtype(2)` 写死 |

### 前置条件

- `CONNECT_isNOTLOGIN(fd)` 必须为真（防重复登录）
- 否则日志 `\n the Client had Logined fd=%d` 并拒绝

### 字符串校验 `checkStringErr` @ `0x808D620`

**非法字节（返回错误 1）**：

| 字节 | 含义 |
|------|------|
| `0x00–0x20` | 控制符与空格（`<= 0x20`） |
| `0x27` | 单引号 `'` |
| `0x3B` | 分号 `;` |
| `0x7F` | DEL |
| `0x80` / `0xFF` | 禁用高字节（另有 GBK 跟踪位） |

失败日志：` StringDog! `；客户端文案 **`异常字符的用户名`**。

### 成功路径

```text
CONNECT_setCdkey(fd, cdkey)
CONNECT_setPasswd(fd, pass)     // strncpy 0x21
CONNECT_setCtype(fd, 2)
CONNECT_setServid(fd, servid)
CONNECT_setMAC(fd, mac)
CONNECT_setState(fd, 2)         // while-login
log: \n登陆账号=%s 密码=%s IP=%s MAC=%s SERVID=%d\n
IP 来自 CONNECT_get_userip，格式 "%d.%d.%d.%d"
```

### 失败文案（ClientLogin_send）

| 条件 | 文案 |
|------|------|
| 账号/密码为空 | `账号或密码不得为空。` |
| 异常字符 | `异常字符的用户名` |
| 账号锁定 | `您的游戏账号已被锁定，无法登录游戏，如有疑问请联系客服。` |
| IP 锁定 | `您的IP已被锁定，无法登录游戏，如有疑问请联系客服。` |
| 密码错误 | `您的密码输入错误，请核对后登录。（注意大小写）` |
| 未注册 | `您的账号尚未注册，请登录主页，注册后登录游戏。` |
| 成功 | `ok`（`0x82FE507`） |

---

## 2. MAC 字段 = 版本令牌（Lua 门禁）

`FreeLoginCheck` @ `0x827037E` 调用 **`data/ablua/freelogincheck.lua`**：

```lua
local key = other.getString(mac, "-", 1)   -- 第一段以 '-' 分割
if key ~= "gmsv" then  →  "版本过旧，请更新后再进入游戏！"
-- mac 仅允许 [0-9a-zA-Z-]
if string.gsub(mac, "[0-9a-zA-Z-]", "") ~= "" then  →  版本过旧
-- 同 MAC+IP 多开限制 maxnum = 1 或 31（按 gameservername）
```

**MAC 线格式：`gmsv-<alnum>`**，例如 `gmsv-1234567890abcdef`。

---

## 3. ClientLogin_send（gmsv → 客户端）

**符号**：`lssproto_ClientLogin_send` @ `0x80A4FF0`

编码路径：

```text
CONNECT_getCdkey(fd) → buf@0x89A4840
strlen(buf)
*(buf+len)   = "1234"   // 0x34333231 LE
*(buf+len+4) = "567"    // 0x373635  → 拼成 cdkey+"1234567"
util_mkstring(result)   // escape 结果文案
util_mkint(dst, escaped) // 追加到发送缓冲
_util_SendMesg(file="lssproto_serv.c", line=0xBFA, fd, code=0x48, msg)
```

> **观察**：固定后缀 **`1234567`** 被拼在全局缓冲的 cdkey 之后（`0x89A4840`）。`ServerDispatchMessage` 亦写同一魔数。登录响应业务参数仍为 **单字符串**（`ok` 或错误文案）。

---

## 4. CharList / CharLogin

| 命令 | recv | 前置 | 动作 |
|------|------|------|------|
| `CharList` | `0x8093600` | `CONNECT_isCLI` 且已 ClientLogin | 取 cdkey/pass/fdid，按 cdkey 枚举角色，回 `CharList_send` |
| `CharLogin` | `0x80940E0` | `CONNECT_isCLI` | `CONNECT_setCharname` → `saacproto_ACCharLogin_send` → `setState(3)` |

CharLogin 错误：`failed` / `Already Logged in\n` / `Can't access char have no name\n` / `你的账号正在离线，请再次登陆！`

---

## 5. gmsv ↔ saac：ACServerLogin

**recv** @ `0x8089CF0`：首参必须等于 **`successful`**（11 字符），然后打横幅：

```text
与AC服务器连接成功！
 正常服务器!
服务端版本: <公益-PW石器特别版 2017年03月23日00:17:17>
...
```

**send** @ `0x809E850` 命令名 `ACServerLogin`，参数 **3** 个：

| # | 编码 |
|---|------|
| 1 | `mkstr_int` |
| 2 | `mkstr_string` |
| 3 | `mkstr_string` |

**ACCharLogin send** @ `0x809B1E0` 命令名 `ACCharLogin`，参数 **5** 个：

`int, string, string, string, string`

---

## 6. 连接槽布局（节选）

```text
conn = base + fd * 0x13AC
+0x1c  cdkey  (string)
+0x58  passwd (string, max 0x21)
```

状态：`CONNECT_setState` — `1`=?, `2`=while-login, `3`=char-login, `isNOTLOGIN` / `isLOGIN` / `isUnderLogin` / `isWhileLogin` / `isCLI`。

---

## Java 实现要点

1. 帧编解码复用 `LssProtocolCodec`
2. 校验实现 `checkStringErr` 字节表（见上）
3. MAC 令牌必须匹配 `^gmsv-[0-9A-Za-z-]+$`
4. 状态机：`NOTLOGIN → (ClientLogin) → WHILE_LOGIN(2) → (CharList) → (CharLogin) → CHAR_LOGIN(3)`
5. 响应文案使用上表字面量（GB2312/GBK）

---

## 7. 线上信封 `util_SendMesg`（重要）

部分业务响应（含 `ClientLogin_send`）**不走** 纯 `mkstr` 帧，而是包一层：

```text
格式串 @ 0x82BE01E:  "&;%d%s;#;"
        & ;  <funcId>  <encodedBody>  ; # ;
```

`_util_SendMesg(file, line, fd, code, body)` @ `0x8099D00`：

```text
funcId = code + 0x17
sprintf(buf, "&;%d%s;#;", funcId, body)
再经 0x8099C00 变换后 lssproto 发送
```

### util_mkstring @ `0x8099220`

- 字母表 **`0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz{}`**（`0x82BE028`）
- 输出后追加 **`;`** 分隔
- 编码密钥缓冲 = 全局 `0x89A4840`（内容为 **cdkey + `1234567`**）

### util_mkint @ `0x8099300`

- 对整数做 **`NOT`（按位取反）** 后再编码
- 格式线索 `3142`（`0x82BE014`）
- 同样字母表 + `;` 结尾
- 密钥同为 `0x89A4840`

### 密钥 `1234567`

`ClientLogin_send` / `ServerDispatchMessage` 均在 `0x89A4840` 的 cdkey 后写入：

```text
"1234" "567"  →  cdkey ++ "1234567"
```

这是私服定制的 **会话编码盐**，Java 侧必须复现。


---

## 响应编码补全（LoginResponses）

所有 `*_send` 共用路径：

```text
key = cdkey + "1234567"
body = util_mkstring(a) [+ util_mkstring(b)]
body += util_mkint(srcLen)          // 源串字节长度（校验/对齐）
wire = "&;{code+0x17}{body};#;"
```

| 发送 | code | funcId | body |
|------|------|--------|------|
| ClientLogin | 0x48 | 95 | `mkstring(result) + mkint(len)` |
| CharList | 0x50 | 103 | `mkstring(a)+mkstring(b)+mkint(lenA+lenB)` |
| CharLogin | 0x4E | 101 | 同上 |
| CreateNewChar | 0x4A | 97 | 同上 |
| CharLogout | 0x52 | 105 | 同上 |

Java：`csa.net.login.LoginResponses`，网关登录路径已切换到信封编码。
