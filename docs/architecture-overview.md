# CSA 石器时代私服 — 架构总览

> 逆向对象：`E:\REA\csa`  
> 制品：`csa/gmsv/gmsvjt`（ELF32 x86, 5.5MB）、`csa/saac/saacjt`（ELF32 x86, 574KB）  
> 版本串：`公益-PW石器特别版 2017年03月23日`

## 进程与拓扑

```text
游戏客户端
    │  lssproto  (文本行协议, port 1234)
    ▼
gmsvjt  ──游戏逻辑 / 战斗 / 地图 / Lua──
    │  saacproto (同一套 mkstr/escape, port 39202)
    ▼
saacjt  ──账号 / 角色存档 / 家族 / 邮件──
    │
    ├─ MySQL 5.5  库 wpcsa（26 表）
    └─ 文件树     saac/char, mail, family, db/...
```

## 技术栈证据

| 证据 | 含义 |
|------|------|
| ELF 32-bit LSB, machine=3 | Linux x86 32 位 |
| `libmysqlclient.so.15` + `sasql_*` | MySQL C API |
| `lua_*` / `NPC_Lua_*` / `data/ablua/*.lua` | Lua 热更（约 90 脚本） |
| `pthread_mutex_*` | 互斥；配置注释提及 `MUTLITHREAD` 可选 |
| `lssproto_*` / `saacproto_*` | 双协议自研文本编解码 |
| 非 Go / 非 Rust | REA `inspect-go-binary` / `inspect-rust-binary` 均无元数据 |

## 配置与数据

| 路径 | 作用 |
|------|------|
| `gmsv/setup.cf` | 游戏参（CPUUSE、缓冲、等级、端口 `port=1234`） |
| `gmsv/sql.cf` | MySQL 连接（库 `wpcsa`，表 `CSAlogin`） |
| `saac/acserv.cf` | 账号服（`LSPORT`/`LSADDRESS`、目录） |
| `gmsv/data/` | 敌人、遇敌、道具、地图、Lua |
| `saac/char/` 等 | 角色/邮件/家族文件存档 |
| `csa.sql` | phpMyAdmin 导出，26 张表 |

## 性能相关观察

- 32 位进程；`recvbuffer/sendbuffer=64K`；大量 `buffer over err` 字符串
- 角色持久化为文件而非 DB；MyISAM/InnoDB 混用；`gb2312`/`latin1`
- `CPUUSE`/`runlevel` 手工调参；`debuglevel=3` 默认全量错误
- `Connectnum=40`、`sameiponlinenum=10` 粗粒度限流

## Java 改造目标（摘要）

1. **协议兼容**：`LssProtocolCodec` 见 `java/`（已完成对拍）
2. **域拆分**：连接网关 / 战斗 / 角色道具宠物 / 账号存档
3. **持久化**：文件角色 → DB + 异步快照；MySQL 8 InnoDB + 连接池
4. **脚本**：LuaJ/GraalJS 先兼容再迁移

## 本机工具链

| 工具 | 路径 |
|------|------|
| REA CLI | `C:\Users\Administrator\AppData\Roaming\npm\rea.cmd` 6.5.0 |
| JDK 21 | `E:\tools\jdk-21.0.12.1+1` |
| Ghidra 12.1.4 | `E:\tools\ghidra_12.1.4_PUBLIC` |
| 环境脚本 | `E:\tools\rea-ghidra-env.ps1` |

> Windows Ghidra P0 **仅 PE / raw 6502**，ELF 细节靠符号表 + capstone（脚本见 `scripts/`）。
