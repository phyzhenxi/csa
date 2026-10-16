import struct
from capstone import Cs, CS_ARCH_X86, CS_MODE_32

path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
TEXT_ADDR, TEXT_OFF = 0x80762d0, 0x2e2d0
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60
DATA_ADDR, DATA_OFF = 0x83033a0, 0x2bb3a0

def va2off(va):
    return va - TEXT_ADDR + TEXT_OFF

def off_of(va):
    if RO_ADDR <= va < RO_ADDR + 0x48cdc:
        return va - RO_ADDR + RO_OFF
    if DATA_ADDR <= va < DATA_ADDR + 0x43168:
        return va - DATA_ADDR + DATA_OFF
    return None

md = Cs(CS_ARCH_X86, CS_MODE_32)

print("===== helper 0x8217950 (attr advantage calc) =====")
va = 0x8217950
# size unknown - dump until ret after reasonable length, use symbol search
# from call pattern it's called with (eax=skill?, edx,ecx,esi/arg4, arg5=5)
# dump 400 bytes
code = data[va2off(va):va2off(va)+400]
for insn in md.disasm(code, va):
    print(f"0x{insn.address:x}:\t{insn.mnemonic}\t{insn.op_str}")
    if insn.mnemonic == "ret" and insn.address > va+20:
        break

print("\n===== constants =====")
for va,kind in [(0x82f69d8,"f32"),(0x82bbfa8,"f64"),(0x82c23f0,"f64"),(0x82c2400,"f64")]:
    o = off_of(va)
    if o is None:
        print(f"0x{va:x} missing"); continue
    if kind=="f32":
        print(f"0x{va:x} f32={struct.unpack_from('<f',data,o)[0]}")
    else:
        print(f"0x{va:x} f64={struct.unpack_from('<d',data,o)[0]}")

# also look for a 5x5 table in rodata near attr code
print("\n===== nearby rodata floats (attr matrix candidates) =====")
# scan rodata for a run of small floats/doubles around 0.5-2.0
base = 0x82bbf00
for off in range(off_of(base), off_of(base)+0x200, 4):
    f = struct.unpack_from("<f", data, off)[0]
    if 0.01 <= abs(f) <= 10.0 and abs(f) not in (0.0,):
        va = RO_ADDR + (off - RO_OFF)
        print(f"0x{va:x} f32={f}")
