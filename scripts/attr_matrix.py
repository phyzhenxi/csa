# Reconstruct AttrAdjust bilinear matrix from the 5x5 form
# A[i] * D[j] terms and their f32/f64 multipliers observed in disassembly
# From instruction stream at 0x821db1e-0x821dd11

# Observed multiplies (order in code):
# Block uses A components as outer multiplier: A[1], A[2], A[0], A[3], A[4]
# against D[0..4] with coefficients 1.5, 0.6, 1.0 and plain adds
# Final: sum * 0.0001 then / float_factor * 0.0001 -> percent scale

# Practical reconstruction used by SA community + our constants:
# element order: 0=Earth, 1=Water, 2=Fire, 3=Wind, 4=None/Rest
# advantage cycle (atk vs def multiplier):
#   Earth->Wind 1.5, Wind->Fire 1.5, Fire->Water 1.5, Water->Earth 1.5
#   reverse is 0.6, same element 1.0

matrix = [
# def: E     W     F     Wi    None
[1.0,  0.6,  1.0,  1.5,  1.0], # atk Earth
[1.5,  1.0,  0.6,  1.0,  1.0], # atk Water
[1.0,  1.5,  1.0,  0.6,  1.0], # atk Fire
[0.6,  1.0,  1.5,  1.0,  1.0], # atk Wind
[1.0,  1.0,  1.0,  1.0,  1.0], # atk None
]
names = ["地 Earth","水 Water","火 Fire","风 Wind","无 None"]
print("相克矩阵 atk\\def |", " | ".join(names))
for i,row in enumerate(matrix):
    print(f"{names[i]:10} |", " | ".join(f"{v:4.1f}" for v in row))

print()
print("GetAttr 伪代码:")
print("""
  a[0] = WORK(0x450) + WORK(0x47C)  // 地 base + equip
  a[1] = WORK(0x451) + WORK(0x47D)  // 水
  a[2] = WORK(0x452) + WORK(0x47E)  // 火
  a[3] = WORK(0x453) + WORK(0x47F)  // 风
  clamp each to [0, 100]
  a[4] = max(0, 100 - (a[0]+a[1]+a[2]+a[3]))  // 无属性
""")

print("AttrAdjust 伪代码:")
print("""
  A = GetAttr(attacker)
  D = GetAttr(defender)
  // special: WORK(0x4CA)==0x7F0 -> force single element via WORK(0x4CC/0x4CD)
  // optional callback CHAR_getSomething(0x12) can rewrite attrs
  // scale A[i] *= ecx  (formation/power factor, often 1)
  score = sum_i sum_j  A[i] * D[j] * M[i][j]
  // M uses 1.5 / 0.6 / 1.0 as above
  score = trunc(score * 0.0001)     // 1/10000
  ratio = trunc(score / float_factor * 0.0001)  // normalize by GetAttr power
  return ratio
""")

print("GetAttr/AttrAdjust 相关字段:")
print("  WORK 0x450-453: 地/水/火/风 基础")
print("  WORK 0x47C-47F: 装备/料理加成")
print("  WORK 0x4CA == 0x7F0: 强制属性模式")
print("  WORK 0x4CC, 0x4CD: 单属性索引与值")
print("  WORK 0x4E8, 0x4EA: 技能/魔法属性类型 1-4")
print("  unit+0x90: 技能属性类型, unit+0x98: 技能威力")
