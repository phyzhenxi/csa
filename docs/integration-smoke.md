# Gateway ↔ SimClient 联调记录

日期：2026-10-16  
命令：`GatewayMain 12345` + `SimClient 127.0.0.1 12345`

## 交互

```text
ClientLogin => 1 ClientLogin ok
CharList   => 2 CharList 0
CharLogin  => 3 CharLogin ok
W 100 200 1 => (no reply)
C          => 5 P hello\Sworld
B          => 6 B 3
TK         => 7 MSG <checkStringErr 拒绝：空格属非法字符>
```

## 结论

- 登录 → 角色列表 → 进角色 **状态机打通**
- 移动 W 静默更新坐标
- 对话 C 回显（空格转义 `\S` 正确）
- 战斗 B 回显 action
- TK 被 `checkStringErr` 拒（空格 `0x20`），与 C 约束一致

## 复现

```powershell
java -cp out csa.client.GatewayMain 12345
java -cp out csa.client.SimClient 127.0.0.1 12345
```
---

## DB 登录联调（2026-10-16）

```text
DbAuth: user1/pass1 => OK / wrong => bad_password / nobody => not_registered
Gateway: ClientLogin => &;95...;#;  (信封)
```

启动：

```powershell
$env:CSA_DB_PASS = "<password>"
java -cp out;jars\mysql-connector-j.jar csa.client.GatewayMain 1234
```

`CSA_DB_DISABLE=1` 可关掉数据库只测协议。

---

## SimClient 四场景联调（DB 后端）

网关：`GatewayMain 12350` + `DbAuth csa@192.168.2.100/csa`

```text
S1 login user1/pass1 => &;95...;#;   成功
S1 CharList / CharLogin / W / C / B  全链路 OK
S2 bad password     => &;95...;#;   非 ok 长串
S3 unknown user     => &;95...;#;
S4 bad mac (eth0)   => &;95...;#;
SIM SCENARIOS PASS
```

工具：`csa.client.SimScenarios`

```powershell
java -cp out;mysql-connector-j.jar csa.client.GatewayMain 12350
java -cp out;mysql-connector-j.jar csa.client.SimScenarios 127.0.0.1 12350
```

---

## CharList 接入 CSAinfo

`DbCharacters.listByCdkey` + `packList` → `CharList_send` 的 data 参数。

```text
DB CHARS PASS
user1 chars: 1
  勇者A|120|1|1|100|5
S1 CharList => &;103...;#;   (含角色摘要)
SIM SCENARIOS PASS
```

---

## 三服务并行集成（CharSaveStore + WalkService + BattleService）

| 服务 | 自测 | 网关接入 |
|------|------|----------|
| CharSaveStore | CHARSAVE STORE PASS | CharLogin 下发全量存档 |
| WalkService | WALK SERVICE PASS | W 逐步校验（禁大跳） |
| BattleService | BATTLE SERVICE PASS | B action→damage |

全量回归 10/10 PASS；SimScenarios 通过（CharLogin 返回长信封）。

---

## 地图加载 + 技能道具命令

- **MapLoader**：楼层 `.dat` = `u32le w,h` + `w*h*6`（3×u16le/格）；战斗图 `"SAB "` + 400 BE u16
- **SkillItemService**：`I/PI` 用道具、`PS` 技能、`SKUP` 升级
- 全量回归 12/12 PASS

---

## TD/FM/WN + 地图 walk-flag

- **TradeFamilyWindowService**：TD 交易（摆摊拒）、FM 家族、WN 窗口（seq/data 校验）
- **walk-flag**：楼层 tile **word3==0 可走**，非 0 挡；void=`0xC000`；graphic=0 可走（旧启发式已纠正）
- 全量回归 **13/13 PASS**

---

## 社交杂项 + 服务端 LS2MAP

- **SocialMiscService**：PMSG 私聊、STREET_VENDOR 摆摊、JOBDAILY 日常
- **ServerMapLoader**：LS2MAP（BE，44B 头 + tile/obj 网格）
- **MapsetWalkTable**：`mapset.txt` col0=graphic id，col3=0 可走/1 挡/2 特殊
- 全量回归 **15/15 PASS**
