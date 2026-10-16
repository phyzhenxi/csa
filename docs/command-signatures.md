# P0-b 高频命令参数签名

来源：`lssproto_*_recv` 栈参数 + `*_send` 编码调用 + 语义串。  
机器可读：[`../data/command-signatures.json`](../data/command-signatures.json)。

## 通用门禁（几乎所有 recv）

```text
CONNECT_checkfd / CONNECT_isCLI / CONNECT_isLOGIN
charaindex = CONNECT_getCharaindex(fd)   @ 0x80813A0
常见状态检查：
  WorkInt(char, 0x47)  == 0     // 不在战斗
  WorkInt(char, 0x137) == -1    // 不在摆摊
  checkStringErr(arg)           // 字符串参数
```

## 发送侧编码

多数 `*_send` 走 **`util_SendMesg` 信封**（见 login-handshake.md §7）：

```text
util_mkstring / util_mkint  →  "&;%d%s;#;"
```

少数仍用 lssproto `mkstr_*` 帧。`CharList_send` / `CharLogin_send` / `CreateNewChar_send` 各 2 个 `util_mkstring`。

---

## 核心命令签名

| 命令 | recv 参数（fd 除外） | 类型 | 语义 |
|------|---------------------|------|------|
| **W** | `x, y, dir` | int,int,int | **移动**。校验 work 0x47（战斗）、坐标；错误 `讯号错误。` / ` NU-Err ` |
| **W2** | `x, y, dir` | int,int,int | 移动扩展（1292B，含 GC `gcgc` 路径） |
| **C** | `data` | string | **对话/窗口**。需非战斗、非摆摊 |
| **B** | `action` | int | **战斗指令**。→ `BATTLE` 路径 |
| **M** | — | — | 地图/状态同步（57B 薄封装） |
| **S** | `data` | string | 状态/显示串 |
| **D** | `data` | string | 道具/丢弃等 |
| **I** | `data` | string | 道具交互 |
| **L** | `data` | string | 环境/落地 |
| **MSG** | `a,b,data` | int,int,string | 消息 |
| **TK** | `data` | string | 聊天（含禁言、等级&lt;10 只能 `hi`） |
| **TD** | `data` | string | 交易；摆摊中拒绝 |
| **WN** | `seqno,objindex,select,data` | int,int,int,string | 窗口；data 限长 0x800，过 `checkStringErr` |
| **PR** | `x,y` 等 | int… | 组队；`改封包!禁止组队区域` |
| **PS** | 多参 | int… | 宠物/技能 |
| **FS** | flags 位域 | int | 功能开关，逐 bit 调 `0x80B03E8` |
| **FM** | data | string | 家族 |
| **PI / MI / DI / DP / ID / DU / EV** | 见 JSON | — | 道具/地图/事件 |
| **ClientLogin** | `cdkey,pass,mac,servid` | str,str,str,int | 见 login-handshake |
| **CharList** | — | — | 角色列表 |
| **CharLogin** | `charname` | string | 进入角色 |
| **CreateNewChar** | name + 形象… | str,int… | 创角 |
| **EN** | x,y | int,int | 丢弃/放置；work 0x46==2 时拒绝 |
| **KN** | petname | string | 改宠物名；len≤0x10，过 `checkStringErr`，`宠物名中请不要带有非法字符！` |

## 典型 recv 栈布局（cdecl）

```text
W_recv:   fd, [ebp+0xc]=a, [ebp+0x10]=b, [ebp+0x14]=c     → 3 参数
W2_recv:  同 W
C_recv:   fd, [ebp+0xc]=data                               → 1 参数
B_recv:   fd, [ebp+0xc]=action                             → 1 参数
ClientLogin_recv: fd, +c, +10, +14, +18                    → 4 参数
CharLogin_recv:   fd, +c=name                              → 1 参数
MSG_recv: fd, +c, +10, +14                                 → 3 参数
```

## 门禁检查 ID（CHAR WorkInt / Int）

| ID | 用途 |
|----|------|
| 0x47 | 战斗中标志（0=空闲） |
| 0x137 | 摆摊（-1=未摆摊） |
| 0x46 | 某模式（EN 要求 ==2） |
| 5 / 6 | 当前 X / Y（`_CHAR_getInt`） |
| 0xE2 | 交易/PK 类开关 |

## 进游戏最小命令集

```text
ClientLogin → CharList → CharLogin → W/C/B/TK...
```

## Java 映射建议

```java
enum Cmd { W, W2, C, B, M, S, TK, TD, WN, PR, ClientLogin, CharList, CharLogin, ... }
record Frame(long id, Cmd cmd, List<Object> args) {}
// 每命令一个 decode(args) → domain event
// 门禁：state.isCLI && !inBattle && !streetVendor && checkStringErr
```

## W / W2 行走校验（WalkService）

Java 实现：`java/src/main/java/csa/map/WalkService.java`（自测 `WalkServiceSelfTest`）。

| 项 | 规则 |
|----|------|
| 参数 | `x, y, dir`（线上 base62 int；见 `data/command-signatures.json`） |
| 合法步 | 同格（原地转向）或 8 方向步进 1 格（Chebyshev ≤ 1） |
| 跳跃 | 位移 > 2 格直接拒绝（`ERR_JUMP_TOO_FAR`）；恰好 2 格非单步 → `ERR_ILLEGAL_STEP` |
| 门禁 | 与其它 recv 相同：非战斗 `WorkInt 0x47==0`、非摆摊 `0x137==-1` |
| apply | `applyMove` 返回新 `{x,y,dir}`，由调用方写回 Session / Domain |
| 地图 | `isWalkable(floor,x,y)` 默认 true；TODO 加载 `嘟嘟石器客户端/map`、`data/battleMap` 的 `.map` |

自测输出：`WALK SERVICE PASS`

## 地图加载（MapLoader）

Java：`java/src/main/java/csa/map/MapLoader.java`（自测 `MapLoaderSelfTest` → `MAP LOADER PASS`）。

| 格式 | 布局 |
|------|------|
| 地图 `map/*.dat`（亦称 `*.MAP`） | `u32le width,height` + 每格 6 字节（3×u16le：图块/物件/标志） |
| 战斗 `data/battleMap/*.map` | 魔数 `SAB ` + 400× `u16be` 图块（804 字节，按 20×20） |

`MapLoader.load(path) → MapData{width,height,byte[] tiles}`；`tiles` 为行走标志字节（0=可走，非 0=不可走）。

**Walk-flag 推断**（多份 `map/*.dat` 比对）：每格第 3 个 `u16le` 为行走属性——`0` 可走，非 `0` 不可走（墙/物件/虚空）。虚空格三字均为 `0xC000`，经 graphic-0 虚空位一并封锁。图块 `0` + 标志 `0` 是合法地板（室内房间/室外地表），**不**按 graphic-0 封锁。自测 `1.dat` 具体格断言 → `WALKFLAG PASS`。

`WalkService.registerMap(floor, map)` 注册后 `isWalkable(floor,x,y)` 走 loader，未注册默认可走。

## TD / FM / WN（TradeFamilyWindowService）

Java 实现：`java/src/main/java/csa/game/TradeFamilyWindowService.java`（自测 `TradeFamilyWindowServiceSelfTest` → `TRADE FAMILY WINDOW PASS`）。

| 命令 | recv 地址 | 参数 | 结果 token | 二进制错误串 |
|------|-----------|------|------------|--------------|
| **TD** | 0x808EA00 | `data`（本层 API：`tradeRequest(player, partner, itemId)`） | `TD ok` / `TD ERR_STREET_VENDOR` / `TD ERR` | `摆摊状态下不能交易` |
| **FM** | 0x808E940 | `data`（本层 API：`familyJoin(name)` / `familyLeave()`） | `FM ok` / `FM ERR` | — |
| **WN** | 0x808F440 | `seqno,objindex,select,data` | `WN ok` / `WN ERR_BAD_WINDOW` / `WN ERR_EVENT` | `改封包!! 非法的窗口号` / `不接受召唤任务` |

| 项 | 规则 |
|----|------|
| TD | 摆摊中（WorkInt 0x137）→ `ERR_STREET_VENDOR`；player/partner 空、自交易、itemId&lt;0 → `ERR` |
| FM | join 空名 → `ERR`；leave 无家族 → `ERR`；join 成功写入 `familyName`，leave 清空 |
| WN | data 限长 **0x800**；seqno 须匹配 `seedExpectedWindow`（-1 表示任意）；`select` 越界或非法窗口号 → `ERR_BAD_WINDOW`；`select=SELECT_SUMMON_TASK(9)` → `ERR_EVENT` |
| state | 可 seed：`streetVendor`、`familyName`、`expectedWindowSeqno`、`lastWindow{seqno,objindex,select,data}` |
| encode | `encodeWindowTokens` / `encodeTradeToken` 走 `LssProtocolCodec.mkInt` / `mkString` |

自测输出：`TRADE FAMILY WINDOW PASS`