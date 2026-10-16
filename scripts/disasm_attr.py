import struct
from capstone import Cs, CS_ARCH_X86, CS_MODE_32

path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
TEXT_ADDR, TEXT_OFF = 0x80762d0, 0x2e2d0
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60
DATA_ADDR, DATA_OFF = 0x83033a0, 0x2bb3a0

def va2off(va):
    return va - TEXT_ADDR + TEXT_OFF

def read_cstr(va):
    for base, off, sz in ((RO_ADDR, RO_OFF, 0x48cdc),(DATA_ADDR, DATA_OFF, 0x43168)):
        if base <= va < base+sz:
            o = va-base+off
            if 0<=o<len(data):
                e = data.index(b"\x00", o)
                return data[o:e].decode("gbk","replace")
    return None

md = Cs(CS_ARCH_X86, CS_MODE_32)

# BATTLE_GetAttr @ 136366446? wait symbol said 0x8217d6e size 596
# 0x8217d6e
va = 0x8217d6e
size = 596
code = data[va2off(va):va2off(va)+size]
print("===== BATTLE_GetAttr @ 0x8217d6e (596) =====")
for insn in md.disasm(code, va):
    print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")

print("\n===== BATTLE_AttrAdjust @ 0x821d8d7 full (1118) =====")
va = 0x821d8d7
size = 1118
code = data[va2off(va):va2off(va)+size]
for insn in md.disasm(code, va):
    print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
