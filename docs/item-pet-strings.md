# 道具 / 技能指令与嵌套串

关联：[`character-save-format.md`](character-save-format.md)（存档段里的 `skill*` / `item*`）、
`data/protocol-commands.json`、`csa.item.ItemPetCodec`、`csa.game.SkillItemService`。

## 嵌套串格式（ITEM / SKILL / PET）

| 类型 | 格式 | 反编译入口 |
|------|------|-----------|
| 道具 mode0 | `key=val\|key=val\|` | `ITEM_makeStringFromItemData` @ `0x81C36DE` |
| 道具 mode1 | `key:val,key:val,` | 同上 |
| 技能 | `lv=%d\|id=%d\|`（尾 `\|` 可裁） | `SKILL_makeStringFromSkillData` @ `0x80DDFA8` |
| 宠物 | `key:value\|` + `psk%d:%d\|` + `ptsk%d:%d\|` + `pitem%d:%s\|` | `CHAR_makePetStringFromPetIndex` @ `0x80B437F` |

Java：`ItemPetCodec.encodeItem/decodeItem`、`encodeSkill/decodeSkill`、`encodePet/decodePet`
（自测 `ItemPetCodecSelfTest` → **ITEM PET ALL PASS**）。

## 技能 / 道具指令（I / PI / PS / SKUP）

线参数：int 走 base62（`LssProtocolCodec.mkInt` / `demInt`），字符串走 escape。
帧：`msgId SP command SP args "\n"`。

| 命令 | 语义 | 结果 token | 状态 |
|------|------|-----------|------|
| **I / PI** | 使用背包道具 | `I ok` / `I ERR` | ✅ SkillItemService |
| **PS** | 对目标施放技能 | `PS ok` + damage-ish int | ✅ SkillItemService |
| **SKUP** | 技能升级（lv++） | `SKUP ok` | ✅ SkillItemService |
| **DI** | 道具丢弃（recv @ `0x80928f0`） | — | 未实现 |
| **TD** | 交易（摆摊中拒） | — | 未实现 |

`SkillItemService`（`java/src/main/java/csa/game/SkillItemService.java`）为内存态实现：

- 背包 `Map<Integer,Integer>`（itemIndex → count），`seedItem` 可预置
- 技能等级 `Map<Integer,Integer>`（skillIndex → level），`seedSkill` 可预置
- `useItem(i)`：count>0 时 `I ok` 并 count--，否则 `I ERR`
- `useSkill(idx, target)`：lv>0 时 `PS ok`，伤害写入 `lastDamage()`（level 缩放 + 种子随机）；否则 `PS ERR`
- `skillUp(idx)`：lv++ 并返回 `SKUP ok`
- `encodeSkillString(idx)`：输出 `lv=N|id=idx|`（与存档 skill 段一致）

自测：`SkillItemServiceSelfTest` → **SKILL ITEM PASS**
