# 战斗属性与伤害骨架

机器可读矩阵见 [`../data/elemental-matrix.json`](../data/elemental-matrix.json)。

## 属性向量 GetAttr

`BATTLE_GetAttr` @ `0x8217D6E`（596 字节）

```text
a[0] 地 = WORK(0x450) + WORK(0x47C)   // 基础 + 装备/料理
a[1] 水 = WORK(0x451) + WORK(0x47D)
a[2] 火 = WORK(0x452) + WORK(0x47E)
a[3] 风 = WORK(0x453) + WORK(0x47F)
clamp each to [0, 100]
a[4] 无 = max(0, 100 - (a[0]+a[1]+a[2]+a[3]))
```

强制单属性：`WORK(0x4CA) == 0x7F0` 时用 `WORK(0x4CC/0x4CD)` 覆盖。

## 相克矩阵

`BATTLE_AttrAdjust` @ `0x821D8D7` — 双线性型 `Σ A[i]·D[j]·M[i][j]`

| atk \ def | 地 | 水 | 火 | 风 | 无 |
|-----------|----|----|----|----|----|
| 地 | 1.0 | **0.6** | 1.0 | **1.5** | 1.0 |
| 水 | **1.5** | 1.0 | **0.6** | 1.0 | 1.0 |
| 火 | 1.0 | **1.5** | 1.0 | **0.6** | 1.0 |
| 风 | **0.6** | 1.0 | **1.5** | 1.0 | 1.0 |
| 无 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |

**克制环**：地 → 风 → 火 → 水 → 地（×1.5 / 被克 ×0.6）

常量：`1.5f @ 0x82F69D8`，`0.6 @ 0x82BBFA8`，`score *= 1e-4 @ 0x82C23F0`。

### 伪代码

```text
A = GetAttr(attacker); D = GetAttr(defender)
score = Σ_i Σ_j A[i] * D[j] * M[i][j]
score *= 0.0001
ratio = trunc(trunc(score) / GetAttrPower * 0.0001)
```

## 单属性技能倍率 `0x8217950`

```text
type = unit.attr (unit+0x90): 1=地 2=水 3=火 4=风
base = unit.power (unit+0x98)
result = 0.5 + base * def_attr[type] * 0.01 * 0.5
       = 0.5 + base * def_attr / 200
default: 0.5
```

## 伤害调用链（骨架，未全式）

```text
BATTLE_CriDamageCalc @ 0x822305B
  base = BATTLE_DamageCalc(atk, def) @ 0x82223A7
  crit = WorkInt(def, 0x65D)
  bonus = crit * Int(atk, 0x65E) / max(Int(def,0x65F),1) * 0.5f
  return trunc(base + bonus)

BATTLE_DamageCalc
  ├─ BATTLE_getRidePet / BATTLE_adjustRidePet3A
  ├─ BATTLE_GetAttr / BATTLE_AttrAdjust
  └─ 百分比: x/100 (0x51EB851F 编译器除 100 魔法)

BATTLE_DamageSubCale @ 0x8220D25
  rideAtk/(rideAtk+defVal) 分摊减伤
```

### 已还原浮点常量（DamageCalc）

| 值 | 用途（推断） |
|----|----------------|
| 0.7 | 等级/属性衰减 |
| 0.3 | 次要衰减 |
| 100.0 / 200.0 | 百分比归一 |
| 8.0 / 7.0 | 技能倍率与除数 |
| 0.5 | 暴击折半 |
| 0.0625 / 0.125 | 细粒度加成 |

## 关键符号

| 符号 | 地址 | 大小 |
|------|------|------|
| BATTLE_DamageCalc | 0x82223A7 | 3252 |
| BATTLE_DamageSub | 0x8227AA7 | 5676 |
| BATTLE_Attack | 0x822B1D7 | 6819 |
| BATTLE_CriticalCheck | 0x821A262 | 83 |
| BATTLE_CriticalCheckPlayer | 0x821A11C | 1094 |
| BATTLE_AttrAdjust | 0x821D8D7 | 1118 |
| BATTLE_GetAttr | 0x8217D6E | 596 |

## Java 注意

- 属性计算是纯函数，适合单测对拍
- x87 `fistp` 截断行为需与 Java `Math.floor` / `(int)` 对齐并做黄金用例

---

## Java 实现

`java/src/main/java/csa/battle/DamageCalculator.java`（自测 **DAMAGE CALC ALL PASS**）

| 函数 | 对应 C | 常量 |
|------|--------|------|
| `baseAttack` | getRidePet 分支 + `/100` pet equip | — |
| `baseDefense` | `* 0.7` | `0x82BC1B0` |
| `ratioDamage` | `*8/7` / `*2` / `*1/16` / `*1/8` 分支 | `8,7,2,0.0625,0.125` |
| `applyResists` | 四槽 `1 - r/200` | `200` |
| `applyRandom` | `(1+(u-0.5)*0.3)` | `0x82D8960` |
| `criticalDamage` | `crit*atk/def*0.5` | `0.5` |
| `calc` | 完整流水线 | trunc=`Math.floor` |

### 仍不确定（标注在源码注释）

- helper `0x8076138`（clamp/rand）
- dword `0x82C35A0 = 0x30000000`（2^-31，fistp 前缩放）
- 四个 resist 打包字段的精确 WorkInt 编号（0x65x 族）

**下一步对拍**：用真实战斗日志/客户端伤害数字校准 `ratioDamage` 分支系数。
