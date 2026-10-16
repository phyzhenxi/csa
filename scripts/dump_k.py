import struct
path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60
def off_of(va):
    return va - RO_ADDR + RO_OFF
for va in (0x82c2408, 0x82c2400, 0x82bd544, 0x82c23f0, 0x82f69d8, 0x82bbfa8):
    o = off_of(va)
    if va >= 0x82c2400 and va < 0x82c2500:
        # doubles region
        print(f"0x{va:x} f64={struct.unpack_from('<d',data,o)[0]}")
    elif va >= 0x82bbf00 and va < 0x82bc000:
        print(f"0x{va:x} f64={struct.unpack_from('<d',data,o)[0] if va%8==0 else struct.unpack_from('<f',data,o)[0]}")
    else:
        print(f"0x{va:x} f32={struct.unpack_from('<f',data,o)[0]}")

# dump doubles around 0x82c2408
print("--- doubles near 0x82c23f0 ---")
o = off_of(0x82c23e0)
for i in range(8):
    va = 0x82c23e0 + i*8
    d = struct.unpack_from("<d", data, off_of(va))[0]
    print(f"0x{va:x} = {d}")
