# 角色存档格式（CHAR_makeStringFromCharData）

机器可读字段表见 [`../data/character-fields.json`](../data/character-fields.json)。

## 序列化入口

| 函数 | 地址 | 作用 |
|------|------|------|
| `CHAR_makeStringFromCharData` | `0x80B4EEB` | 角色 → 文本 |
| `CHAR_makeStringFromCharIndex` | `0x80B47C8` 附近 | 索引路径 |
| `CHAR_makeCharFromStringToArg` | `0x80B1302` | 文本 → 角色 |
| `makeStringFromEscaped` / `makeCharFromEscaped` | `0x807E4B0` / `0x807D550` | 转义 |

## 线格式

```text
<intKey>=<intValue>\n          × 289   (表基址 0x8317DBC)
<strKey>=<escapedString>\n     × 14    (表基址 0x8318260, 槽宽 128, 角色偏移 0x488)
flg<i>=<byte>\n                      (角色偏移 0xB88)
skill<i>=<escaped>\n
item<i>=<escaped>\n
poolitem<i>=<escaped>\n
title<i>=<int>\n
adr<i>=<escaped>\n
pet<i>=<escaped>\n
poolpet<i>=<escaped>\n
DATAEND=<checksum>\n
```

- 写入缓冲：BSS `0x83467C0`，上限 **131072（0x20000）** 字节
- 溢出检测字符串：`err chardata buffer over`、`DATAENDCHECKPOINT`
- 文件命名：`%s.%d.char.%d.%d.%d.%d.%d.%d`，目录 `char/0x%x` 哈希分桶（saac 侧）

## 字符串字段（14）

`name, ownt, arg, ocd, ocn, fmname, ucode, tsi, tsn, love, lovrrid, lovrrneme, newname, powerup`

## 整型字段分组（289）

| 索引 | 组 | 代表 key |
|------|-----|----------|
| 1–23 | core | `pn,bi,bbi,fb,fl,x,y,dir,lv,gld,hp,mp,mmp,vi,str,tou,dx,chr,luc,aea,awa,afi,awi` |
| 24–55 | combat_status | `slt,cr,cou,...,poi,par,sil,sto,dar,con` |
| 56–64 | growth | `exp,nexp,lte,skup,lvup,...` |
| 65–132 | events | `evt1–32`, `nev1–32` |
| 133–168 | elemental_fame | `trn,fmi,fame,earth_exp,...,wind_defmagic_exp` |
| 169–226 | extended | 融合/职业/骑宠/VIP 等 |
| 248–278 | lua | `luasave_int_*`, `luasave_workint_*` |
| 279–289 | misc | `title_1..3,yhp..ylv,reserverreward` |

完整 289 表以 JSON 为准（由符号表 `0x8317DBC` 导出）。

## 转义

`makeStringFromEscaped`：处理 `\`（`0x5C`）与 `0xFF`；与协议层 `escapeString` 规则同族（空格/换行/反斜杠）。

## Java 映射建议

```text
CharacterSave
  ints: Map<CharacterIntField, int>   // enum 289 键
  strings: Map<CharacterStrField, String>
  flags: byte[]
  items/pets/skills: List<String>     // 各自已是转义串或结构化后再编
  dataEnd: int
```

兼容策略：先原样读写 `key=value` 文本，再演进 JSON/二进制；保留 `DATAEND` 校验。

---

## Java 实现

`java/src/main/java/csa/save/CharacterSaveCodec.java`：

- `encode(Save)` → `key=value\n` 文本（289 int + 14 string + flg/skill/item/... + `DATAEND`）
- `decode(String)` → `Save`
- 转义与协议层同规则（`\\` `\S` `\n` `\r`，CJK 双字节）
- **解析优先级**：先精确匹配 int/string 字段表，再走 section 前缀（避免 `titlelv`/`title_d` 被 `title*` 误吞）
- 缓冲上限 `0x20000`

自测：`CharacterSaveCodecSelfTest` → **SAVE CODEC ALL PASS**（含 289 字段全量 round-trip）

---

## 道具 / 宠物 / 技能嵌套串

详见 `data/item-pet-strings.json` 与 `java/.../ItemPetCodec.java`（**ITEM PET ALL PASS**）。

| 类型 | 格式 | 入口 |
|------|------|------|
| **道具** | `key=val\|key=val\|`（mode0）或 `key:val,key:val,`（mode1） | `ITEM_makeStringFromItemData` @ `0x81C36DE` |
| **技能** | `lv=%d\|id=%d\|`（去尾 `\|`） | `SKILL_makeStringFromSkillData` @ `0x80DDFA8` |
| **宠物** | `key:value\|` + `psk%d:%d\|` + `ptsk%d:%d\|` + `pitem%d:%s\|` | `CHAR_makePetStringFromPetIndex` @ `0x80B437F` |

道具字段表 40 项（`id,bi,tg,dib,upin,dmce,...,forname`），结构 16 字节记录 `name_ptr, pad, index, pad` @ `0x832FDA0`。  
宠物 int 字段沿用角色 289 名，但分隔符是 **`:`** 而非 `=`。
