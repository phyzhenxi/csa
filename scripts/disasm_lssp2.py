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
                e=data.index(b"\x00",o)
                return data[o:e].decode("gbk","replace")
    return None

def read_cstr_file(va):
    # also try raw file as if va is file offset
    return read_cstr(va)

print("=== format strings ===")
for va in (0x82c063f, 0x82c0085, 0x82bb4d0, 0x82bb50c, 0x82bb50e):
    s = read_cstr(va)
    print(f"0x{va:x}: {s!r}")

# globals
print("\n=== globals used by mkstr ===")
for va in (0x8313e24, 0x8313e30, 0x8313e34):
    o = va - DATA_ADDR + DATA_OFF
    if 0<=o<len(data):
        val = struct.unpack_from("<I", data, o)[0]
        print(f"0x{va:x} -> 0x{val:x} str={read_cstr(val)!r}")

md = Cs(CS_ARCH_X86, CS_MODE_32)

def dump(name_or_va, size, limit=80):
    if isinstance(name_or_va, int):
        va, sz = name_or_va, size
        label = f"fn@0x{va:x}"
    else:
        va, sz = name_or_va, size
        label = name_or_va
    code = data[va2off(va):va2off(va)+sz]
    print(f"\n===== {label} @ 0x{va:x} size={sz} =====")
    n=0
    refs=[]
    for insn in md.disasm(code, va):
        n+=1
        if n<=limit:
            print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
        for tok in insn.op_str.replace(","," ").split():
            if tok.startswith("0x"):
                try:
                    v=int(tok,16)
                    if RO_ADDR<=v<RO_ADDR+0x48cdc:
                        refs.append(v)
                except: pass
    print(f"... total {n}")
    if refs:
        print("-- string refs --")
        for v in sorted(set(refs)):
            print(f"  0x{v:x}: {read_cstr(v)!r}")

# helpers called by mkstr_int
dump(0x80abf20, 48)   # int to string?
dump(0x80ab7b0, 64)   # cat with sep?
dump(0x80ab800, 64)   # append fmt?
dump(0x80abb90, 48)   # get message id?
dump(0x80abc90, 80)   # parse int for demkstr
dump("lssproto_demkstr_string", 56)
dump("lssproto_wrapStringAddr", 40)
dump("lssproto_consumeLine", 89)
dump("lssproto_descapeString", 80)
