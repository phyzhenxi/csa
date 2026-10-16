# lssproto / saacproto 线协议契约

机器可读命令表见 [`../data/protocol-commands.json`](../data/protocol-commands.json)。  
Java 实现见 [`../java/src/main/java/csa/net/lssp/LssProtocolCodec.java`](../java/src/main/java/csa/net/lssp/LssProtocolCodec.java)。

## 帧格式

```text
帧    := 头 参数* LF
头    := 消息ID SP 命令名 SP
消息ID:= %u  自增计数器
参数  := base62整数 | 转义字符串，空格分隔
分帧  := '\n'（consumeLine）
切分  := SP 0x20；CR/LF 截断 token（splitString）
```

`CreateHeader` 格式串：`"%u %s "` @ `0x82C0085`  
saacproto 同格式：`saacproto_CreateHeader` @ `0x80A27D0`

### 示例

```text
7 Login hello\Sworld G\n
```

- `7` = msgId  
- `Login` = 命令  
- `hello\Sworld` = 转义后的 `"hello world"`  
- `G` = base62(42)

## 整数编码（base62）

字母表 @ `0x82BB4D0`：

```text
0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ
```

| 字符 | 值 |
|------|-----|
| `0`–`9` | 0–9 |
| `a`–`z` | 10–35 |
| `A`–`Z` | 36–61 |

- 编码：`cnv10to62` @ `0x80ABD90`（连除 62）
- 解码：`demkstr_int` → `0x80ABC90`（可选 `-` 前缀）
- 例：`42→G`，`86→1o`，`123→1Z`

## 字符串转义

`escapeString` @ `0x80AB870` / `descapeString` @ `0x80AB9C0`

| 原始 | 线上 |
|------|------|
| `\` | `\\` |
| 空格 | `\S` |
| `\n` | `\n` |
| `\r` | `\r` |
| 字节 ≥0x80 | 原样 2 字节对（GBK/CJK） |

反转义：`\S→SP`，`\n→LF`，`\r→CR`，`\\→\`；**未知转义吞掉下一字节并输出 `\`**（与 C `0x80ABA62` 一致）。

## 相关符号（节选）

| 符号 | VA | 大小 |
|------|-----|------|
| lssproto_CreateHeader | 0x80AC4F0 | 43 |
| lssproto_cnv10to62 | 0x80ABD90 | 398 |
| lssproto_escapeString | 0x80AB870 | 262 |
| lssproto_descapeString | 0x80AB9C0 | 224 |
| lssproto_mkstr_int | 0x80AC2B0 | 86 |
| lssproto_demkstr_int | 0x80ABD10 | 20 |
| lssproto_splitString | 0x80AC640 | 301 |
| lssproto_consumeLine | 0x80ABAE0 | 89 |
| lssproto_ServerDispatchMessage | 0x80A7060 | 17805 |

## 命令规模

| 族 | 唯一命令名 |
|----|------------|
| lssproto | **99** |
| saacproto | **73** |

方向均含 `_send` / `_recv` 两侧符号（详见 JSON）。

## 登录相关

`ServerDispatchMessage` 识别魔术串 @ `0x82C01D0`：

```text
&;129;PtdT8oWQ4kSK}qYT7nVP3jPK9pYS6mUO2gPU8pXR5lTN;vvVemr;#;
```

- 部分路径使用 `';'` 分隔  
- 含异常字符检测：`封包使用异常字符%s`  
- 命令哈希/分发：对 token 中 `'a'`/`'9'` 计数后与阈值比较（`0x27`/`0x1d`）——**参数签名表尚未还原（P0-b）**

## saacproto

编解码与 lssproto **同构**（各自全局缓冲：`0x8313E24/30/34` vs `0x8313DE4/DF4`），可复用 `LssProtocolCodec`，仅命令集与参数不同。

## 验证状态

`LssProtocolCodecSelfTest`：**25 项 ALL PASS**（base62 / escape / frame round-trip）。

---

## util_mk 黄金向量对拍

`scripts/util_golden.py` 按 capstone 反汇编写 Python 参考实现，生成 `data/util-golden.json`（45 条）。  
`UtilGoldenTest` 用其中 24 条对拍 Java `UtilCodec` → **UTIL GOLDEN ALL PASS**。

| 算法 | 说明 |
|------|------|
| `swapint("3142")` | 4 字节置换：dest[0]=src[2], dest[1]=src[0], dest[2]=src[3], dest[3]=src[1] |
| `util_256to64_shl` | 3 字节→4 字符，`(bits - key[i]) & 0x3f` → alphabet |
| `util_256to64_shr` | 同布局，`(bits + key[i]) & 0x3f` |
| 密钥 | 空则 `cary`；登录路径 `cdkey+"1234567"` |
| 尾缀 | `;`；信封 `&;%d%s;#;` |

**限制**：对拍是 Python↔Java 一致性（均源自同一反汇编），尚未与真机 C 输出逐字节比对。真客户端联调时若信封不匹配，以真实帧为准再校准。
