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
    for base, off, sz in ((RO_ADDR, RO_OFF, 0x48cdc), (DATA_ADDR, DATA_OFF, 0x43168)):
        if base <= va < base + sz:
            o = va - base + off
            if 0 <= o < len(data):
                e = data.index(b"\x00", o)
                return data[o:e].decode("gbk","replace")
    return None

md = Cs(CS_ARCH_X86, CS_MODE_32)

# Full BATTLE_DamageCalc disasm focusing on math
va, size = 136455079, 3252
code = data[va2off(va):va2off(va)+size]
print("===== BATTLE_DamageCalc math-related =====")
for insn in md.disasm(code, va):
    m = insn.mnemonic
    if m in ("fild","fistp","fmul","fdiv","fadd","fsub","fsubp","faddp","fmulp","fdivp","imul","idiv","mul","div","sar","shr","sal","shl","lea","cmp","test") or "0x51eb851f" in insn.op_str:
        print(f"0x{insn.address:x}:\t{m}\t{insn.op_str}")

print("\n===== BATTLE_CriDamageCalc full =====")
va, size = 136458331, 207
code = data[va2off(va):va2off(va)+size]
for insn in md.disasm(code, va):
    print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")

print("\n===== BATTLE_DamageSubCale full =====")
va, size = 136449317, 256
code = data[va2off(va):va2off(va)+size]
for insn in md.disasm(code, va):
    print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")

# symbol: 0x821f948 = first call in DamageCalc - look up
print("\n===== resolve helper symbols near battle =====")
# parse symtab quickly for names near those addrs
e_shoff = struct.unpack_from("<I", data, 32)[0]
e_shentsize = struct.unpack_from("<H", data, 46)[0]
e_shnum = struct.unpack_from("<H", data, 48)[0]
e_shstrndx = struct.unpack_from("<H", data, 50)[0]
sections=[]
for i in range(e_shnum):
    off=e_shoff+i*e_shentsize
    sections.append(struct.unpack_from("<IIIIIIIIII", data, off))
shstr=sections[e_shstrndx]
shstrtab=data[shstr[4]:shstr[4]+shstr[5]]
def sname(so):
    e=shstrtab.index(b"\x00",so); return shstrtab[so:e].decode("ascii","replace")
# symtab
symsec=sections[2]  # type 2 is SYMTAB - find by type
for s in sections:
    if s[1]==2:
        symsec=s; break
strsec=sections[symsec[6]]
strtab=data[strsec[4]:strsec[4]+strsec[5]]
count=symsec[5]//16
helpers={0x821f948,0x80adb4a,0x80ad3a4,0x8219736,0x8219e1c,0x8217d6e,0x81c91a6}
for i in range(count):
    o=symsec[4]+i*16
    st_name,st_value,st_size,st_info,st_other,st_shndx=struct.unpack_from("<IIIBBH", data, o)
    if st_value in helpers or (st_value and abs(st_value-0x821f948)<8):
        e=strtab.index(b"\x00",st_name) if st_name else o
        nm=strtab[st_name:e].decode("ascii","replace") if st_name else ""
        print(f"  0x{st_value:x} size={st_size} {nm}")
