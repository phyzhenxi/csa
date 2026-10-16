import struct, sys
from capstone import Cs, CS_ARCH_X86, CS_MODE_32

path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path, "rb").read()

# parse ELF32 section headers
e_shoff = struct.unpack_from("<I", data, 32)[0]
e_shentsize = struct.unpack_from("<H", data, 46)[0]
e_shnum = struct.unpack_from("<H", data, 48)[0]
e_shstrndx = struct.unpack_from("<H", data, 50)[0]
shoff = e_shoff
sections = []
for i in range(e_shnum):
    off = e_shoff + i * e_shentsize
    (name_off, typ, flags, addr, offset, size, link, info, addralign, entsize) = struct.unpack_from("<IIIIIIIIII", data, off)
    sections.append(dict(name_off=name_off, typ=typ, flags=flags, addr=addr, offset=offset, size=size))
shstr = sections[e_shstrndx]
shstrtab = data[shstr["offset"]:shstr["offset"]+shstr["size"]]
def sname(s):
    o = s["name_off"]
    e = shstrtab.index(b"\x00", o)
    return shstrtab[o:e].decode("ascii", "replace")
for s in sections:
    s["name"] = sname(s)
    if s["name"] in (".text", ".rodata", ".data", ".bss"):
        print(f"{s['name']}: addr=0x{s['addr']:x} off=0x{s['offset']:x} size=0x{s['size']:x}")

text = next(s for s in sections if s["name"] == ".text")
rodata = next(s for s in sections if s["name"] == ".rodata")

def va2off(va):
    return va - text["addr"] + text["offset"]

# symbols we care about
targets = {
    "CHAR_makeStringFromCharData": (134958827, 2673),
    "CHAR_makeCharFromStringToArg": (134943490, 3071),
    "BATTLE_DamageCalc": (136455079, 3252),
    "BATTLE_DamageSub": (136477351, 5676),
    "BATTLE_AttrAdjust": (136435927, 1118),
    "BATTLE_CriticalCheck": (136421986, 83),
    "BATTLE_CriDamageCalc": (136458331, 207),
    "makeStringFromEscaped": (134735024, 138),
}

md = Cs(CS_ARCH_X86, CS_MODE_32)
md.detail = False

def disasm(name, va, size, limit=80):
    off = va2off(va)
    code = data[off:off+size]
    print(f"\n===== {name} va=0x{va:x} off=0x{off:x} size={size} =====")
    # collect referenced immediates in code range that look like rodata addrs
    refs = set()
    n = 0
    for insn in md.disasm(code, va):
        n += 1
        if n <= limit:
            print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
        # capture absolute addresses
        for tok in insn.op_str.replace(",", " ").split():
            if tok.startswith("0x"):
                try:
                    v = int(tok, 16)
                    if rodata["addr"] <= v < rodata["addr"] + rodata["size"]:
                        refs.add(v)
                except: pass
    print(f"... total instructions: {n}")
    # print referenced strings
    print(f"-- string refs ({len(refs)}) --")
    for v in sorted(refs):
        o = v - rodata["addr"] + rodata["offset"]
        end = data.index(b"\x00", o)
        s = data[o:end]
        try:
            print(f"  0x{v:x}: {s.decode('gbk','replace')[:80]!r}")
        except:
            print(f"  0x{v:x}: {s[:40]!r}")

for name, (va, sz) in targets.items():
    if name in ("BATTLE_DamageCalc", "CHAR_makeStringFromCharData"):
        disasm(name, va, sz, 100)
    else:
        disasm(name, va, sz, 30)
