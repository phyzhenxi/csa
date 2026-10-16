# 剩余工作与优先级

## 状态总览

| 领域 | 状态 | 说明 |
|------|------|------|
| 架构画像 | ✅ | 双进程 + MySQL + Lua + 文件存档 |
| 角色存档格式 | ✅ | 289 int + 14 string + 段结构 |
| 属性相克 | ✅ | 5×5 矩阵 1.5/0.6 + GetAttr |
| 伤害公式 | 🟡 骨架 | 常量与调用链已出，全分支未完 |
| 线协议 codec | ✅ | Java ALL PASS |
| 命令参数签名 | ❌ | 仅命令名清单 |
| 登录握手 | ❌ | 魔术串已知，交互顺序未完 |
| 存档 Java 实现 | ❌ | 仅字段表与常量 |
| Lua / 玩法系统 | ❌ | 未展开 |
| 全量函数反编译 | ❌ | 5600+ 中只做了关键路径 |

## P0 还剩什么？

**协议编解码 P0（帧/base62/escape）已完成。**  
若以「客户端能登录进游戏」为 P0 标准，仍缺：

### P0-a 登录/鉴权握手（✅ 已完成）

- `ClientLogin` / `CharLogin` / `ACServerLogin` 时序
- 魔术串 `&;129;PtdT8oWQ4kSK}...#;` 的发送时机与响应
- `CSAlogin` 表字段（Name/PassWord/MAC/IP/Lock）如何映射

**验收**：Java 网关能完成登录到角色列表，不依赖原版二进制。

### P0-b 进游戏高频命令参数签名（✅ 已完成）

至少覆盖：

| 命令 | 用途 |
|------|------|
| `ClientLogin` | 登录 |
| `CharList` / `CharLogin` | 角色列表/进入 |
| `W` / `W2` | 移动 |
| `C` / `M` / `S` | 对话/地图/状态 |
| `B` / `BATTLESKILL` | 战斗 |
| `D` / `I` / `L` | 道具等 |

**验收**：每条命令有参数顺序与 base62/字符串类型表，可用真实抓包或符号 `*_recv` 反汇编核对。

> 除此之外 **没有新的、独立的 P0**。saacproto 可复用同一 codec，属 P0-b 的并行子集。

## P1 — 数据与战斗可玩（存档/伤害/道具宠物已落地）

| 任务 | 验收 |
|------|------|
| 存档 encode/decode Java（✅ CharacterSaveCodec） | 与 `CHAR_makeStringFromCharData` 对拍 289+14 字段 |
| `BATTLE_DamageCalc` 全式 | 黄金用例覆盖暴击/骑宠/百分比分支 |
| item/pet 嵌套串 | `ITEM_makeStringFromItemData` 等 |
| `saacproto` 命令表（✅ SaacCommands） | 账号域完整 |

## P2 — 玩法与运维

- Lua API 语义 → Java 服务接口  
- 地图/遇敌（`encount.txt` / `enemy*.txt`）  
- 家族 FM、商城、摆摊  
- 迁移与双跑、指标与压测  

## 环境限制

| 限制 | 影响 | 缓解 |
|------|------|------|
| Windows Ghidra P0 不收 ELF | 无 C 伪代码 | capstone 切片；或 Linux Ghidra |
| 无 Hopper/IDA | 仅 Ghidra+capstone | 按需 |
| 角色样本文件为空 | 无真实存档样例 | 需你方提供或自建号 |

## 建议下一步（确认后执行）

1. **P0-a 登录握手**（从 `lssproto_ClientLogin_recv` 与 `ServerDispatchMessage` 分发表切入）  
2. **P0-b 命令签名**（`W`/`C`/`B` 三条优先）  
3. **P1 存档 Java**（`csa.save` 包）

---

## 数据库接入（2026-10-16）

| 项 | 值 |
|----|-----|
| 主机 | `192.168.2.100:3306`（MariaDB） |
| 用户 | `root` |
| 库名 | `csa` / 原 dump 为 `wpcsa` |
| 凭据 | 仅 `config/db.local.json`（**gitignore**） |

**当前状态**：TCP 可达，登录被拒：

```text
Host '192.168.2.8' is not allowed to connect to this MariaDB server
```

**需在 DB 服务器执行**：

```sql
CREATE USER IF NOT EXISTS 'csa'@'192.168.2.%' IDENTIFIED BY '0OKM)okm123';
GRANT ALL PRIVILEGES ON csa.* TO 'csa'@'192.168.2.%';
-- 若库名是 wpcsa：
GRANT ALL PRIVILEGES ON wpcsa.* TO 'csa'@'192.168.2.%';
FLUSH PRIVILEGES;
```

或临时放行 root：

```sql
GRANT ALL PRIVILEGES ON *.* TO 'root'@'192.168.2.%' IDENTIFIED BY '0OKM)okm123';
FLUSH PRIVILEGES;
```

复测：`python scripts/db_probe.py`

### 数据库接入结果

- 用户 `csa@192.168.2.%` 可连库 `csa`（26 表，结构同 csa.sql）
- `CSAlogin` / `CSAinfo` 已核对；已种测试账号 `user1/pass1`
- Java `csa.db.DbAuth`：`DB AUTH PASS`（对/错/未注册）
- 注意：`Lock` 为 MariaDB 保留字，SQL 需反引号

## LS2MAP / mapset（gmsv 服务器地图，2026-10-16）

**格式**（`data/map/**`，全 BE；1373/1391 样本精确匹配，其余有多余尾部字节）：

```text
offset  size  field
0       6     magic "LS2MAP"
6       2     u16be map id（与数字文件名一致）
8       32    GBK 地图名，常以 "|0" 结尾，NUL 填充
40      2     u16be width
42      2     u16be height
44      w*h*4 cells: u16be tile graphic, u16be object graphic
```

**mapset.txt**（`data/map/mapset.txt`）：空白分隔行；col0 = tile/obj graphic id（不是 floor/map id）；**col3 = 走路属性** `0` 开放 / `1` 阻挡 / `2` 特殊。实测 17452 条：0=11082, 1=6283, 2=87。LS2MAP 格子的 tile/obj id 在表内 100% 命中。

**走路判定**（`ServerMapLoader.cellWalkAttr` / `isWalkable`）：

- 出界 → 阻挡
- tile 查 mapset（未知按阻挡）
- obj id `0` = 空物件，不挡（尽管 mapset id 0 自身 col3=1）
- 任一层阻挡 → 1；否则任一层特殊 → 2；否则 0
- `isWalkable` 仅开放(0)为可走

**Java**：`csa.map.ServerMapLoader` / `csa.map.MapsetWalkTable` / `csa.map.ServerMapSelfTest`（输出 `SERVER MAP PASS`）。
