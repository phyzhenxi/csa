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

# symbols
e_shoff = struct.unpack_from("<I", data, 32)[0]
e_shentsize = struct.unpack_from("<H", data, 46)[0]
e_shnum = struct.unpack_from("<H", data, 48)[0]
sections=[]
for i in range(e_shnum):
    sections.append(struct.unpack_from("<IIIIIIIIII", data, e_shoff+i*e_shentsize))
symsec=None
for s in sections:
    if s[1]==2: symsec=s; break
strsec=sections[symsec[6]]
strtab=data[strsec[4]:strsec[4]+strsec[5]]
syms={}
for i in range(symsec[5]//16):
    o=symsec[4]+i*16
    st_name,st_value,st_size,st_info,st_other,st_shndx=struct.unpack_from("<IIIBBH", data, o)
    if st_name==0: continue
    e=strtab.index(b"\x00",st_name)
    nm=strtab[st_name:e].decode("ascii","replace")
    syms[nm]=(st_value,st_size)

md = Cs(CS_ARCH_X86, CS_MODE_32)

def dump(name, limit=70):
    va,sz = syms[name]
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
    for v in sorted(set(refs))[:25]:
        print(f"  ref 0x{v:x}: {read_cstr(v)!r}")

dump("lssproto_descapeString", 70)
dump("lssproto_consumeLine", 50)
dump("lssproto_splitString", 70)
dump("lssproto_demkstr_string", 40)
dump("lssproto_wrapStringAddr", 30)
dump("lssproto_CreateHeaderID", 30)

# ServerDispatchMessage is huge - just first 100 ins and string refs
va,sz = syms["lssproto_ServerDispatchMessage"]
code = data[va2off(va):va2off(va)+min(sz,2500)]
print(f"\n===== ServerDispatchMessage first ~80 ins @ 0x{va:x} =====")
n=0; refs=[]
for insn in md.disasm(code, va):
    n+=1
    if n<=80:
        print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
    for tok in insn.op_str.replace(","," ").split():
        if tok.startswith("0x"):
            try:
                v=int(tok,16)
                if RO_ADDR<=v<RO_ADDR+0x48cdc: refs.append(v)
            except: pass
print(f"... scanned {n} of {sz} bytes")
for v in sorted(set(refs))[:40]:
    print(f"  ref 0x{v:x}: {read_cstr(v)!r}")
