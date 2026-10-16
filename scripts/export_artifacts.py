import struct, json, re
path = r"E:\REA\csa\csa\gmsv\gmsvjt"
data = open(path,"rb").read()
DATA_ADDR, DATA_OFF = 0x83033a0, 0x2bb3a0
RO_ADDR, RO_OFF = 0x82b9f60, 0x271f60

def read_cstr(va):
    if RO_ADDR <= va < RO_ADDR + 0x48cdc:
        o = va - RO_ADDR + RO_OFF
    elif DATA_ADDR <= va < DATA_ADDR + 0x43168:
        o = va - DATA_ADDR + DATA_OFF
    else:
        return None
    if o < 0 or o >= len(data):
        return None
    e = data.index(b"\x00", o)
    return data[o:e].decode("gbk","replace")

# --- character fields ---
int_names = {}
base = 0x8317dbc - DATA_ADDR + DATA_OFF
for i in range(1, 0x122+1):
    ptr = struct.unpack_from("<I", data, base + i*4)[0]
    nm = read_cstr(ptr) if ptr else None
    if nm:
        int_names[i] = nm
str_names = []
base2 = 0x8318260 - DATA_ADDR + DATA_OFF
for i in range(0, 0xe):
    ptr = struct.unpack_from("<I", data, base2 + i*4)[0]
    str_names.append(read_cstr(ptr) if ptr else None)

# semantic groups
def group(idx, name):
    if idx <= 23: return "core"
    if idx <= 55: return "combat_status"
    if idx <= 64: return "growth"
    if idx <= 132: return "events"
    if idx <= 168: return "elemental_fame"
    if idx <= 226: return "extended"
    if name.startswith("luasave"): return "lua"
    return "misc"

fields = {
  "source": {
    "binary": "E:\\REA\\csa\\csa\\gmsv\\gmsvjt",
    "sha256": "8436303454217982e12e8c995519343147794a11959b7e19f9048a149c42c543",
    "int_table_va": "0x8317DBC",
    "string_table_va": "0x8318260",
    "serializer": "CHAR_makeStringFromCharData @ 0x80B4EEB",
    "deserializer": "CHAR_makeCharFromStringToArg @ 0x80B1302"
  },
  "wire": {
    "format": "text key=value lines",
    "int_fmt": "%s=%d\\n",
    "string_fmt": "%s=%s\\n",
    "flag_fmt": "flg%d=%d\\n",
    "skill_fmt": "skill%d=%s\\n",
    "item_fmt": "item%d=%s\\n",
    "poolitem_fmt": "poolitem%d=%s\\n",
    "title_fmt": "title%d=%d\\n",
    "adr_fmt": "adr%d=%s\\n",
    "pet_fmt": "pet%d=%s\\n",
    "poolpet_fmt": "poolpet%d=%s\\n",
    "end_fmt": "DATAEND=%d\\n",
    "buffer_limit": 131072,
    "string_slot_bytes": 128,
    "string_base_offset": "0x488",
    "flag_base_offset": "0xB88",
    "escape": "makeStringFromEscaped / makeCharFromEscaped"
  },
  "int_fields": [{"index": i, "key": n, "group": group(i, n)} for i,n in sorted(int_names.items())],
  "string_fields": [{"index": i, "key": n} for i,n in enumerate(str_names) if n],
}
out = r"E:\REA\csa-rea-artifacts\data\character-fields.json"
with open(out, "w", encoding="utf-8") as f:
    json.dump(fields, f, ensure_ascii=False, indent=2)
print("character-fields.json ints=", len(int_names), "strings=", len([x for x in str_names if x]))

# --- elemental matrix ---
elem = {
  "source": {
    "get_attr": "BATTLE_GetAttr @ 0x8217D6E",
    "attr_adjust": "BATTLE_AttrAdjust @ 0x821D8D7",
    "single_attr": "0x8217950",
    "constants": {
      "advantage_1_5_f32": {"va": "0x82F69D8", "value": 1.5},
      "disadvantage_0_6_f64": {"va": "0x82BBFA8", "value": 0.6},
      "half_f32": {"va": "0x82BD544", "value": 0.5},
      "pct_scale_0_01_f64": {"va": "0x82C2408", "value": 0.01},
      "score_scale_1e_4_f64": {"va": "0x82C23F0", "value": 0.0001}
    }
  },
  "order": ["earth", "water", "fire", "wind", "none"],
  "get_attr": {
    "work_base": {"earth": "0x450", "water": "0x451", "fire": "0x452", "wind": "0x453"},
    "work_equip_bonus": {"earth": "0x47C", "water": "0x47D", "fire": "0x47E", "wind": "0x47F"},
    "clamp_each": [0, 100],
    "none_formula": "max(0, 100 - sum(earth,water,fire,wind))",
    "force_mode_work": "0x4CA == 0x7F0",
    "force_index_work": "0x4CC",
    "force_value_work": "0x4CD"
  },
  "matrix_advantage": {
    "description": "atk \\ def multiplier; 1.5 = counter, 0.6 = countered, 1.0 = neutral/same",
    "cycle": "earth > wind > fire > water > earth",
    "table": {
      "earth": {"earth": 1.0, "water": 0.6, "fire": 1.0, "wind": 1.5, "none": 1.0},
      "water": {"earth": 1.5, "water": 1.0, "fire": 0.6, "wind": 1.0, "none": 1.0},
      "fire":  {"earth": 1.0, "water": 1.5, "fire": 1.0, "wind": 0.6, "none": 1.0},
      "wind":  {"earth": 0.6, "water": 1.0, "fire": 1.5, "wind": 1.0, "none": 1.0},
      "none":  {"earth": 1.0, "water": 1.0, "fire": 1.0, "wind": 1.0, "none": 1.0}
    }
  },
  "attr_ratio_pseudocode": "score = sum_i sum_j A[i]*D[j]*M[i][j]; score*=1e-4; ratio = floor(floor(score)/power*1e-4)",
  "single_attr_multiplier": {
    "formula": "0.5 + base * def_attr[type] * 0.01 * 0.5  ==  0.5 + base*def_attr/200",
    "type_map": {"1": "earth", "2": "water", "3": "fire", "4": "wind", "default": "0.5"},
    "unit_offsets": {"power": "0x98", "attr_type": "0x90"}
  }
}
out = r"E:\REA\csa-rea-artifacts\data\elemental-matrix.json"
with open(out, "w", encoding="utf-8") as f:
    json.dump(elem, f, ensure_ascii=False, indent=2)
print("elemental-matrix.json written")

# --- protocol commands from symbols ---
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
cmds = {"lssproto": [], "saacproto": []}
codec = []
for i in range(symsec[5]//16):
    o=symsec[4]+i*16
    st_name,st_value,st_size,st_info,st_other,st_shndx=struct.unpack_from("<IIIBBH", data, o)
    if st_name==0: continue
    e=strtab.index(b"\x00",st_name)
    nm=strtab[st_name:e].decode("ascii","replace")
    if nm.startswith("lssproto_") and (nm.endswith("_send") or nm.endswith("_recv")):
        body = nm[len("lssproto_"):]
        direction = "send" if body.endswith("_send") else "recv"
        name = body.rsplit("_",1)[0]
        cmds["lssproto"].append({"command": name, "direction": direction, "symbol": nm, "va": f"0x{st_value:x}", "size": st_size})
    elif nm.startswith("saacproto_") and (nm.endswith("_send") or nm.endswith("_recv")):
        body = nm[len("saacproto_"):]
        direction = "send" if body.endswith("_send") else "recv"
        name = body.rsplit("_",1)[0]
        cmds["saacproto"].append({"command": name, "direction": direction, "symbol": nm, "va": f"0x{st_value:x}", "size": st_size})
    elif re.match(r"lssproto_(mkstr|demkstr|escape|descape|CreateHeader|cnv10to62|splitString|consumeLine)", nm):
        codec.append({"symbol": nm, "va": f"0x{st_value:x}", "size": st_size})

# unique command names
def uniq(items):
    seen={}
    for it in items:
        seen.setdefault(it["command"], {"command": it["command"], "send": None, "recv": None})
        seen[it["command"]][it["direction"]] = {"symbol": it["symbol"], "va": it["va"], "size": it["size"]}
    return list(seen.values())

proto = {
  "source_binary": "gmsvjt",
  "frame": {
    "header_format": "%u %s ",
    "header_va": "0x82C0085",
    "create_header": "lssproto_CreateHeader @ 0x80AC4F0",
    "line_terminator": "\\n",
    "field_separator": " ",
    "integer_encoding": "base62 0-9a-zA-Z",
    "integer_alphabet_va": "0x82BB4D0",
    "string_escape": {
      "backslash": "\\\\",
      "space": "\\S",
      "lf": "\\n",
      "cr": "\\r",
      "cjk": "byte>=0x80 copied as 2-byte pair (GBK)"
    },
    "handshake_magic": "&;129;PtdT8oWQ4kSK}qYT7nVP3jPK9pYS6mUO2gPU8pXR5lTN;vvVemr;#;",
    "handshake_magic_va": "0x82C01D0"
  },
  "codec_symbols": codec,
  "lssproto_commands": uniq(cmds["lssproto"]),
  "saacproto_commands": uniq(cmds["saacproto"]),
}
out = r"E:\REA\csa-rea-artifacts\data\protocol-commands.json"
with open(out, "w", encoding="utf-8") as f:
    json.dump(proto, f, ensure_ascii=False, indent=2)
print("protocol-commands.json lss=", len(proto["lssproto_commands"]), "saac=", len(proto["saacproto_commands"]), "codec=", len(codec))
