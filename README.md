# CSA REA 逆向工程成果包

针对 `E:\REA\csa`（石器时代私服 `gmsvjt` + `saacjt`）的逆向基线，供后续 **高性能 Java 服务** 开发。

生成时间：2026-10-16 · 工具：REA 6.5.0 + capstone 5.0.7 + Ghidra 12.1.4（环境就绪，Windows P0 不适用于 ELF）

## 目录

```text
README.md                          ← 本文件
docs/
  compose/spec/csa-rea-reverse-engineering.md   # 任务/范围/验收
  architecture-overview.md         # 总架构
  character-save-format.md         # 角色存档
  battle-attribute-system.md       # 属性相克 + 伤害骨架
  protocol-lssproto.md             # 线协议契约
  remaining-work.md                # 剩余工作 / P0 澄清
data/
  character-fields.json            # 289 int + 14 string
  elemental-matrix.json            # 5x5 相克矩阵
  protocol-commands.json           # 99 lssproto + 73 saacproto 命令
java/
  src/main/java/csa/net/lssp/      # 协议 codec（自测 ALL PASS）
  src/main/java/csa/save/          # 存档字段常量
scripts/                           # capstone 导出脚本
```

## 快速使用

### Java 协议 codec

```powershell
$env:JAVA_HOME = "E:\tools\jdk-21.0.12.1+1"
cd E:\REA\csa-rea-artifacts\java
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d out `
  src\main\java\csa\net\lssp\LssProtocolCodec.java `
  src\main\java\csa\net\lssp\LssProtocolCodecSelfTest.java `
  src\main\java\csa\save\CharacterFields.java
& "$env:JAVA_HOME\bin\java.exe" -cp out csa.net.lssp.LssProtocolCodecSelfTest
```

期望输出：`ALL PASS`

### 重新导出字段表

```powershell
$env:PYTHONPATH = "E:\tools\pylibs"
& $env:MIMO_PYTHON scripts\export_artifacts.py
```

## 已验证结论（摘要）

| 主题 | 结论 |
|------|------|
| 线协议 | `%u %s ` 头 + base62 整数 + 空格参数 + LF |
| 转义 | `\\` `\S` `\n` `\r`；CJK 双字节对 |
| 存档 | `key=value` 文本，289+14 字段，`DATAEND=` |
| 属性 | 地→风→火→水→地，1.5 / 0.6 |
| 登录魔术串 | `&;129;PtdT8oWQ4kSK}...#;` |

## P0 状态

- ✅ 协议编解码  
- ❌ **P0-a 登录握手**、**P0-b 命令参数签名**（详见 `docs/remaining-work.md`）

## 下游建议

1. 先做 P0-a/P0-b，打通登录进游戏  
2. P1 存档与伤害对拍  
3. 再铺 Java 域服务（Netty 网关 + 战斗 + 账号）

## Java 模块

- `csa.net.lssp` 协议 codec
- `csa.net.login` 登录契约
- `csa.net.cmd` 命令表
- `csa.save` 角色存档
- `csa.battle` 伤害计算
- `csa.gateway` 网关骨架（登录→命令）
