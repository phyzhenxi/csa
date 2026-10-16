import struct
from capstone import Cs, CS_ARCH_X86, CS_MODE_32

path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
TEXT_ADDR, TEXT_OFF = 0x80762d0, 0x2e2d0
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60

def va2off(va):
    return va - TEXT_ADDR + TEXT_OFF

# parse symtab for lssproto/saacproto encode/decode helpers
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
symsec=None
for s in sections:
    if s[1]==2: symsec=s; break
strsec=sections[symsec[6]]
strtab=data[strsec[4]:strsec[4]+strsec[5]]
count=symsec[5]//16
syms={}
for i in range(count):
    o=symsec[4]+i*16
    st_name,st_value,st_size,st_info,st_other,st_shndx=struct.unpack_from("<IIIBBH", data, o)
    if st_name==0: continue
    e=strtab.index(b"\x00",st_name)
    nm=strtab[st_name:e].decode("ascii","replace")
    syms[nm]=(st_value,st_size)

want = [n for n in syms if n.startswith(("lssproto_mkstr","lssproto_demkstr","lssproto_escape","lssproto_descape","lssproto_CreateHeader","lssproto_ServerDispatch","lssproto_stringwrapper","lssproto_wrapString","lssproto_splitString","lssproto_consumeLine","lssproto_cnv10to62","lssproto_InitServer","saacproto_CreateHeader","saacproto_mkstr_int","saacproto_demkstr_int","saacproto_escapeString","saacproto_ClientDispatch"))]
print("=== codec-related symbols ===")
for n in sorted(want):
    v,s = syms[n]
    print(f"0x{v:x} {s:5d}  {n}")

# disassemble core helpers
md = Cs(CS_ARCH_X86, CS_MODE_32)

def dump(name, max_ins=80):
    if name not in syms:
        print(f"missing {name}"); return
    va,sz = syms[name]
    code = data[va2off(va):va2off(va)+sz]
    print(f"\n===== {name} @ 0x{va:x} size={sz} =====")
    n=0
    for insn in md.disasm(code, va):
        n+=1
        if n<=max_ins:
            print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
    print(f"... total {n}")

dump("lssproto_mkstr_int", 60)
dump("lssproto_demkstr_int", 60)
dump("lssproto_escapeString", 80)
dump("lssproto_CreateHeader", 80)
dump("lssproto_stringwrapper", 50)
dump("lssproto_cnv10to62", 60)
