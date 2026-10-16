---
feature: csa-rea-reverse-engineering
status: delivered
updated: 2026-10-16
branch: (no git)
commits: 
---

# CSA REA 逆向工程成果基线

## Report

**What was built** — 针对 `E:\REA\csa` 石器时代私服（`gmsvjt`/`saacjt` ELF32）的完整逆向成果包，落在 `E:\REA\csa-rea-artifacts`：架构总览、角色存档字段表（289 int + 14 string）、属性相克矩阵（5×5，1.5/0.6）、lssproto/saacproto 线协议契约（99+73 命令）、Java 协议 codec（25 项自测 ALL PASS）、以及可复现的 capstone 导出脚本。未建 git（用户选择）。

**Verification** — `javac` + `LssProtocolCodecSelfTest` → ALL PASS（25 asserts）；三个 data JSON 解析通过（289/14 字段，99 lss / 73 saac 命令）；Reviewer 复核后修正 2 处 critical（README 导出脚本路径、spec 中 290→289）。

**Journey log** —
1. Windows Ghidra P0 不收 ELF，改用 ELF 符号表 + capstone，仍拿到完整字段/协议契约。
2. npm 在坏代理（127.0.0.1:7897）下会卡死；清空 proxy 后安装成功。
3. 存档整型字段实为 **289**（索引 1..289），早期口述 290 已在产物中统一为 289。
4. descape 未知转义是「输出 `\` 并吞掉下一字节」，不是重解析——与 C `0x80ABA62` 对齐后测试通过。
5. 产物目录独立于原私服包，避免污染 `E:\REA\csa`。

## [S1] Problem

目标 `E:\REA\csa` 是「石器时代」私服服务端（`gmsvjt` / `saacjt` 两个 ELF32 x86 制品 + MySQL `wpcsa` + Lua 脚本）。需要：

1. 用 REA / 静态逆向搞清架构、存档、协议、战斗，以便改造成高性能 Java 服务；
2. 将已获得的全部逆向成果固化为可继续开发的产物（文档、结构化数据、Java codec、脚本）；
3. 明确剩余工作与是否还有 P0 未完成项。

约束：本机 Windows Ghidra P0 **不接受 ELF**，故 ELF 细节由 ELF 符号表 + capstone 反汇编完成；Ghidra/JDK 已就绪供后续 PE 或 Linux 使用。工作区无 git（用户指定只落盘）。

## [S2] Design

### 2.1 产物布局（工作区 `E:\REA\csa-rea-artifacts`）

```text
csa-rea-artifacts/
  README.md                          # 索引与使用说明
  docs/
    compose/spec/csa-rea-reverse-engineering.md  # 本文档
    architecture-overview.md         # 进程/协议/存储总览
    character-save-format.md         # 存档文本格式与字段语义
    battle-attribute-system.md       # 属性相克与伤害骨架
    protocol-lssproto.md             # 线协议契约
    remaining-work.md                # 剩余工作与 P0/P1/P2
  data/
    character-fields.json            # 289 int + 14 string 字段表
    elemental-matrix.json            # 5x5 相克矩阵与 GetAttr 映射
    protocol-commands.json           # lssproto / saacproto 命令名清单
  java/
    src/main/java/csa/net/lssp/      # 协议 codec（已 ALL PASS）
    src/main/java/csa/save/          # 存档字段常量
  scripts/                           # capstone 反汇编/导出脚本
```

### 2.2 已固化的契约

| 域 | 契约 | 证据 |
|----|------|------|
| 线协议 | `msgId SP cmd SP args* LF` | `CreateHeader` `%u %s ` @ `0x80AC4F0` |
| 整数 | base62 `0-9a-zA-Z`，负号 `-` | `cnv10to62` @ `0x80ABD90` |
| 字符串转义 | `\`→`\\`，SP→`\S`，LF→`\n`，CR→`\r` | `escapeString` @ `0x80AB870` |
| CJK | 字节 ≥0x80 按 2 字节对拷贝（GBK） | escape/descape 高字节路径 |
| 分帧 | LF 结束；SP 切 token | `consumeLine` / `splitString` |
| 存档 | `key=value\n` 文本 + `DATAEND=` | `CHAR_makeStringFromCharData` @ `0x80B4EEB` |
| 存档字段 | 289 int + 14 string + flg/item/pet 段 | 名称表 @ `0x8317DBC` / `0x8318260` |
| 属性 | 地水火风无，克制 1.5 / 被克 0.6 | `BATTLE_GetAttr` / `AttrAdjust` |
| 登录魔术串 | `&;129;PtdT8oWQ4kSK}...#;` | `ServerDispatchMessage` @ `0x82C01D0` |

### 2.3 剩余工作分级

- **P0（协议可对接）— 已完成**：帧格式、base62、escape、Java codec（25 项自测 ALL PASS）。
- **P0 补充（登录可通，仍属阻塞级）**：
  - P0-a 登录/鉴权握手与魔术串交互（`ClientLogin` / `ACServerLogin`）
  - P0-b 高频命令参数签名（`W` 移动、`B` 战斗、`C` 对话…）至少覆盖进游戏路径
- **P1（数据与战斗可玩）**：存档 encode/decode Java、`BATTLE_DamageCalc` 全式、道具/宠物串
- **P2（玩法）**：Lua API、地图遇敌、家族/商城等

### 2.4 接口 / 数据流

```text
gmsvjt  --saacproto-->  saacjt  --SQL/文件-->  MySQL + char/mail/family
   ^
   | lssproto
客户端
```

Java 目标：Netty 网关（lssproto codec）→ 游戏域服务 → Account/持久化。

## [S3] Out of Scope

- 全量 5600+ 函数反编译（仅关键路径）
- 运行私服 / 破解 / 攻击
- Hopper / IDA 安装
- 完整 Java 游戏服实现（本轮只交付逆向基线 + 协议 codec）
- 在 Windows Ghidra 上对 ELF 出伪代码（P0 限制，不在本轮绕过）

## Tasks

- [x] T1: 建立产物目录结构与 README 索引 — acceptance: 目录树与 README 可导航全部成果 (covers: S2.1)
- [x] T2: 导出 data/character-fields.json 完整字段表 — acceptance: 289 int + 14 string 与二进制表一致 (covers: S2.2)
- [x] T3: 导出 data/elemental-matrix.json 与 battle 文档 — acceptance: 5x5 矩阵 1.5/0.6 与 GetAttr 映射完整 (covers: S2.2)
- [x] T4: 导出 data/protocol-commands.json 命令清单 — acceptance: lssproto/saacproto 符号表命令名齐全 (covers: S2.2; depends: T1)
- [x] T5: 落地 docs/* 五份技术文档 — acceptance: architecture/save/battle/protocol/remaining-work 可独立阅读 (covers: S2.1,S2.2,S2.3)
- [x] T6: 迁入 Java codec + save 字段常量 + 自测 — acceptance: 编译并 ALL PASS (covers: S2.2)
- [x] T7: 迁入 scripts/ 逆向脚本 — acceptance: 脚本可在配好 PYTHONPATH 后复现导出 (covers: S2.1)
- [x] T8: remaining-work 明确 P0 补充项 — acceptance: P0-a/P0-b 与 P1/P2 边界无歧义 (covers: S2.3)
