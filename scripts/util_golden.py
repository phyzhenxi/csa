# Reference implementation of util_256to64_shl / shr / swapint from gmsvjt disassembly.
# Generate golden vectors for Java UtilCodecSelfTest.

ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz{}"
DEFAULT_KEY = "cary"

def swapint(value, fmt="3142"):
    src = [(value) & 0xFF, (value>>8)&0xFF, (value>>16)&0xFF, (value>>24)&0xFF]
    dst = [0,0,0,0]
    for i,ch in enumerate(fmt[:4]):
        si = (ord(ch) & 0xFF) - 0x31
        if 0 <= si < 4:
            dst[i] = src[si]
    return dst[0] | (dst[1]<<8) | (dst[2]<<16) | (dst[3]<<24)

def encode_256to64(src: bytes, key: str, subtract: bool) -> str:
    """subtract=True -> shl path (bits-key); False -> shr (bits+key)."""
    if not src:
        return ""
    if not key:
        key = DEFAULT_KEY
    k = [ord(c) & 0xFF for c in key]
    out = []
    carry = 0
    ki = 0
    for i, b in enumerate(src):
        shift = (i % 3) * 2
        bits = ((b & 0xFF) << shift) | carry
        kb = k[ki]
        ki += 1
        if ki >= len(k):
            ki = 0
        idx = (bits - kb) if subtract else (bits + kb)
        idx &= 0x3F
        out.append(ALPHABET[idx])
        carry = bits >> 6
        if i % 3 == 2:
            kb = k[ki]
            ki += 1
            if ki >= len(k):
                ki = 0
            idx = (carry - kb) if subtract else (carry + kb)
            idx &= 0x3F
            out.append(ALPHABET[idx])
            carry = 0
            ki = 0
    if carry:
        kb = k[ki]
        idx = (carry - kb) if subtract else (carry + kb)
        out.append(ALPHABET[idx & 0x3F])
    return "".join(out)

def mk_string(s, key):
    return encode_256to64(s.encode("gbk", errors="replace"), key, True) + ";"

def mk_int(v, key):
    swapped = swapint(v, "3142")
    notv = (~swapped) & 0xFFFFFFFF
    raw = bytes([notv & 0xFF, (notv>>8)&0xFF, (notv>>16)&0xFF, (notv>>24)&0xFF])
    return encode_256to64(raw, key, False) + ";"

# golden vectors
vectors = []
for key in ["cary", "user1234567", "abcd1234567"]:
    for s in ["", "a", "ab", "abc", "hello", "Hello World!", "测试", "id=1|"]:
        try:
            vectors.append(("mkString", key, s, mk_string(s, key)))
        except Exception as e:
            vectors.append(("mkString", key, s, f"ERR {e}"))
    for v in [0, 1, 42, -1, 2147483647, -2147483648, 0x11223344]:
        vectors.append(("mkInt", key, str(v), mk_int(v, key)))

print("=== GOLDEN VECTORS ===")
for t,k,inp,out in vectors:
    print(f"{t}|{k}|{inp}|{out}")

# sanity: alphabet all in range
ok = all(set(o.replace(";","")) <= set(ALPHABET) for _,_,_,o in vectors if not o.startswith("ERR"))
print("ALPHA_OK", ok)

# write JSON for Java test
import json
js = []
for t,k,inp,out in vectors:
    js.append({"op": t, "key": k, "input": inp, "output": out})
json.dump(js, open(r"E:\REA\csa-rea-artifacts\data\util-golden.json","w",encoding="utf-8"), ensure_ascii=False, indent=2)
print("wrote util-golden.json count", len(js))
