# Windows 服务端（csa2）与 GM 资料

## Windows 服务端 `E:\REA\csa\csa2\服务端`

| 项 | 说明 |
|----|------|
| 启动器 | `石器时代WIN版服务端管理器.exe` |
| 游戏服 | `gmsv\gmsv.exe`（cygwin1.dll） |
| 账号服 | `saac\saac.exe` |
| 数据源 | **Access** `saac\csadb.mdb`（ODBC `UID=sa PWD=123456`） |
| 配置 | `config.ini`（路径/时间/启动）、`gmsv\setup.cf` |
| 清档 | `清档_双击运行.bat` |

`config.ini` 示例：
```ini
[数据源]
UID=sa
PWD=123456
DBQ=D:\服务端\saac\csadb.mdb
[启动]
GMSV=D:\服务端\gmsv\gmsv.exe
SAAC=D:\服务端\saac\saac.exe
```

> Windows 版用 **MDB**，Linux 版用 **MySQL** —— Java 侧仍以 MySQL `192.168.2.100/csa` 为准；MDB 仅作对照。

## GM 体系

### 账号权限 `gmsv/data/gmset.txt`

```text
账号    权限
judingwan    3
```

权限 1–3（3 最高）。改完需**重启服务端**。

### 调用格式

```text
[gm 密码 指令 参数...]
```

`gm` 密码在 `setup.cf` 配置；中括号必须打。

### 常用命令（节选，详见 docs/gm-commands.txt）

| 命令 | 作用 |
|------|------|
| `gold 数值` | 加钱 |
| `petmake 宠物ID [等级]` | 加宠物 |
| `additem 数量 物品ID` | 加物品 |
| `delpet all/位置` | 删宠物 |
| `delitem all/位置` | 删物品 |
| `send floor x y 帐号` | 传送 |
| `supersend floor x y 格数` | 范围传送 |
| `itemreload` | 重载物品表 |
| `noenemy on/off` | 遇敌开关 |
| `silent 帐号 分钟` | 禁言 |
| `skywalker on/off` | 穿图 |
| `shutdown` | 停服 |
| `create_pet X Y` | 测试宠 |
| `set_momentum 玩家 气势` | 设气势 |
| `set_regist 火 水 电` | 抗性 |
| `angelcreate/clean/info` | 精灵召唤任务 |
| `set_manor_owner` / `manorpk` | 庄园 |

### 相关工具 `E:\REA\csa\gm\`

物品编辑器、NPC 制作器、代码查询器、装备生成器等。

## 对 Java 服务的意义

1. **GM 命令面**可作为 `LssGateway` 的 `TK`/`C` 聊天命令解析器输入（`[gm ...]`）
2. **gmset.txt** 对应权限模型（1–3）
3. **Windows MDB** 说明历史上有 Access 后端，迁移时可对照表结构
4. **清档脚本**提示角色/账号数据生命周期
