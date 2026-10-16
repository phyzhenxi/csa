import struct
path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60
DATA_ADDR, DATA_OFF = 0x83033a0, 0x2bb3a0

def off_of(va):
    if RO_ADDR <= va < RO_ADDR + 0x48cdc:
        return va - RO_ADDR + RO_OFF
    if DATA_ADDR <= va < DATA_ADDR + 0x43168:
        return va - DATA_ADDR + DATA_OFF
    return None

addrs = {
    0x82bc1b0: "qword",
    0x82c3598: "dword",
    0x82c35a0: "dword",
    0x82c3644: "dword",
    0x82f5bf0: "dword",
    0x82f5bf4: "dword",
    0x82f69f4: "dword",
    0x82f69f8: "dword",
    0x82f69fc: "dword",
    0x82f6a00: "dword",
    0x82f6a04: "dword",
    0x82d8960: "qword",
    0x82bd544: "dword",
    0x82c39bc: "dword",
    0x82c2400: "qword",
}
for va, kind in addrs.items():
    o = off_of(va)
    if o is None:
        print(f"0x{va:x} {kind} <out of section>")
        continue
    if kind == "dword":
        f = struct.unpack_from("<f", data, o)[0]
        i = struct.unpack_from("<i", data, o)[0]
        print(f"0x{va:x} dword float={f:.6f} int={i}")
    else:
        d = struct.unpack_from("<d", data, o)[0]
        print(f"0x{va:x} qword double={d:.6f}")
