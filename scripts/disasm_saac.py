import struct
from capstone import Cs, CS_ARCH_X86, CS_MODE_32

path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
TEXT_ADDR, TEXT_OFF = 0x80762d0, 0x2e2d0
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60

def va2off(va):
    return va - TEXT_ADDR + TEXT_OFF

def read_cstr(va):
    if RO_ADDR <= va < RO_ADDR + 0x48cdc:
        o = va - RO_ADDR + RO_OFF
        if 0 <= o < len(data):
            e = data.index(b"\x00", o)
            return data[o:e].decode("gbk","replace")
    return None

md = Cs(CS_ARCH_X86, CS_MODE_32)

def dump(va, sz, name, limit=50):
    code = data[va2off(va):va2off(va)+sz]
    print(f"\n===== {name} @ 0x{va:x} size={sz} =====")
    n=0; refs=[]
    for insn in md.disasm(code, va):
        n+=1
        if n<=limit:
            print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
        for tok in insn.op_str.replace(","," ").split():
            if tok.startswith("0x"):
                try:
                    v=int(tok,16)
                    if RO_ADDR<=v<RO_ADDR+0x48cdc: refs.append(v)
                except: pass
    print(f"... total {n}")
    for v in sorted(set(refs)):
        print(f"  ref 0x{v:x}: {read_cstr(v)!r}")

dump(0x80a27d0, 64, "saacproto_CreateHeader")
dump(0x80a2590, 86, "saacproto_mkstr_int")
dump(0x80a1ff0, 20, "saacproto_demkstr_int")

# cnv10to62 output alphabet confirmation + decode a few test values conceptually
print("\n=== base62 alphabet at 0x82bb4d0 ===")
print(repr(read_cstr(0x82bb4d0)))

# GetNewMessageID is 0x80abb90 - already know it increments 0x8313e50
print("\n=== header format 0x82c0085 ===")
print(repr(read_cstr(0x82c0085)))
print("sep-ish 0x82c063f:", repr(read_cstr(0x82c063f)))
print("dispatch sep 0x83029b7:", repr(read_cstr(0x83029b7)))
print("magic 0x82c01d0:", repr(read_cstr(0x82c01d0)))
