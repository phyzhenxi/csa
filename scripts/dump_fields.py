import struct
path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
# .data addr=0x83033a0 off=0x2bb3a0 size=0x43168
# .rodata addr=0x82b9f60 off=0x271f60 size=0x48cdc
DATA_ADDR, DATA_OFF = 0x83033a0, 0x2bb3a0
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60

def read_cstr(va):
    if RO_ADDR <= va < RO_ADDR + 0x48cdc:
        o = va - RO_ADDR + RO_OFF
    elif DATA_ADDR <= va < DATA_ADDR + 0x43168:
        o = va - DATA_ADDR + DATA_OFF
    else:
        return f"<0x{va:x}>"
    if o < 0 or o >= len(data):
        return f"<bad 0x{va:x}>"
    e = data.index(b"\x00", o)
    return data[o:e].decode("gbk","replace")

# int field name table at 0x8317dbc, 0x122 entries (ebx 1..0x122)
print("=== INT FIELD NAMES (0x8317dbc, 0x122 entries) ===")
base = 0x8317dbc - DATA_ADDR + DATA_OFF
for i in range(1, 0x122+1):
    ptr = struct.unpack_from("<I", data, base + i*4)[0]
    if ptr == 0: continue
    name = read_cstr(ptr)
    if name and len(name) < 40:
        print(f"{i:3d}: {name}")

print("\n=== STRING FIELD NAMES (0x8318260, 0xe entries) ===")
base2 = 0x8318260 - DATA_ADDR + DATA_OFF
for i in range(0, 0xe):
    ptr = struct.unpack_from("<I", data, base2 + i*4)[0]
    name = read_cstr(ptr) if ptr else "-"
    print(f"{i:3d}: {name}")
