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
