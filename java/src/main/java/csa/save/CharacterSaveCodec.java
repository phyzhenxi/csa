package csa.save;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Character save codec matching CHAR_makeStringFromCharData / CHAR_makeCharFromStringToArg.
 *
 * Wire (text lines):
 *   int fields 1..289:  "key=value\n"
 *   string fields 0..13: "key=escaped\n"
 *   then flg/skill/item/poolitem/title/adr/pet/poolpet sections
 *   terminator "DATAEND=n\n"
 *
 * Buffer limit 0x20000. String slots are 128 bytes at char+0x488 in C;
 * Java uses String and enforces UTF-8/GBK length optionally.
 */
public final class CharacterSaveCodec {

    public static final Charset GBK = Charset.forName("GBK");
    public static final int SAVE_BUFFER_LIMIT = 0x20000;
    public static final int INT_FIELD_COUNT = 289;
    public static final int STRING_FIELD_COUNT = 14;

    /** Int field keys in C table order (index 1..289). */
    public static final String[] INT_KEYS = {
        "pn",
        "bi",
        "bbi",
        "fb",
        "fl",
        "x",
        "y",
        "dir",
        "lv",
        "gld",
        "hp",
        "mp",
        "mmp",
        "vi",
        "str",
        "tou",
        "dx",
        "chr",
        "luc",
        "aea",
        "awa",
        "afi",
        "awi",
        "slt",
        "cr",
        "cou",
        "rar",
        "rst",
        "cvo",
        "ml",
        "hl",
        "di",
        "ieqt",
        "poi",
        "par",
        "sil",
        "sto",
        "dar",
        "con",
        "loc",
        "dc",
        "wc",
        "tc",
        "dmc",
        "gpc",
        "kpc",
        "dpc",
        "smc",
        "mic",
        "dbc",
        "dwc",
        "dlc",
        "dswc",
        "dslc",
        "dmswc",
        "wht",
        "wint",
        "lint",
        "exp",
        "nexp",
        "lte",
        "skup",
        "lvup",
        "ity",
        "nc",
        "pnc",
        "llt",
        "duel",
        "evt",
        "evt2",
        "evt3",
        "evt4",
        "evt5",
        "evt6",
        "evt7",
        "evt8",
        "evt9",
        "evt10",
        "evt11",
        "evt12",
        "evt13",
        "evt14",
        "evt15",
        "evt16",
        "evt17",
        "evt18",
        "evt19",
        "evt20",
        "evt21",
        "evt22",
        "evt23",
        "evt24",
        "evt25",
        "evt26",
        "evt27",
        "evt28",
        "evt29",
        "evt30",
        "evt31",
        "evt32",
        "nev",
        "nev2",
        "nev3",
        "nev4",
        "nev5",
        "nev6",
        "nev7",
        "nev8",
        "nev9",
        "nev10",
        "nev11",
        "nev12",
        "nev13",
        "nev14",
        "nev15",
        "nev16",
        "nev17",
        "nev18",
        "nev19",
        "nev20",
        "nev21",
        "nev22",
        "nev23",
        "nev24",
        "nev25",
        "nev26",
        "nev27",
        "nev28",
        "nev29",
        "nev30",
        "nev31",
        "nev32",
        "trn",
        "teq",
        "ini",
        "silent",
        "fmi",
        "fmlf",
        "fmspr",
        "bankgld",
        "ridepet",
        "learnride",
        "lowridepet",
        "limitlv",
        "fusioncode",
        "fusionindex",
        "fusionraise",
        "fusionbe",
        "futimelimit",
        "fusiontype",
        "fame",
        "sindex",
        "earth_exp",
        "water_exp",
        "fire_exp",
        "wind_exp",
        "earth_resist",
        "water_resist",
        "fire_resist",
        "wind_resist",
        "earth_attmagic_exp",
        "water_attmagic_exp",
        "fire_attmagic_exp",
        "wind_attmagic_exp",
        "earth_defmagic_exp",
        "water_defmagic_exp",
        "fire_defmagic_exp",
        "wind_defmagic_exp",
        "personaglod",
        "gamblenums",
        "actsty",
        "evbasevtl",
        "evbasestr",
        "evbasetgh",
        "evbasedex",
        "fmtimelimit",
        "profession_class",
        "profession_level",
        "profession_skill_point",
        "attachpile",
        "profession_fire_p",
        "profession_ice_p",
        "profession_thunder_p",
        "profession_fire_r",
        "profession_ice_r",
        "profession_thunder_r",
        "herofloor",
        "becomepig",
        "becomepig_bbi",
        "leavetime",
        "momentum",
        "aexp",
        "aexpt",
        "herocnt",
        "tsf",
        "memberpoint",
        "vipride",
        "viptime",
        "beatitude",
        "lowridepet1",
        "highridepet2",
        "tc01",
        "tc02",
        "tc03",
        "tc04",
        "tc05",
        "tc06",
        "tc07",
        "tc08",
        "tc09",
        "tc10",
        "tc11",
        "tc12",
        "tc13",
        "tc14",
        "tc15",
        "tc16",
        "tc17",
        "tc18",
        "tc19",
        "tc20",
        "tc21",
        "tc22",
        "tc23",
        "tc24",
        "tc25",
        "locked",
        "super",
        "onlinetime",
        "cameo",
        "mt",
        "mttime",
        "evolve",
        "fdt",
        "active",
        "titlelv",
        "titletime",
        "mtime",
        "regtime",
        "regcount",
        "regoltime",
        "spcounter",
        "sptime",
        "offtime",
        "fmregtime",
        "canequip",
        "tensefield",
        "luasave_workint_1",
        "luasave_workint_2",
        "luasave_workint_3",
        "luasave_int_1",
        "luasave_int_2",
        "luasave_int_3",
        "npctime",
        "report",
        "luasave_int_4",
        "luasave_int_5",
        "luasave_int_6",
        "luasave_int_7",
        "luasave_int_8",
        "luasave_int_9",
        "luasave_int_10",
        "luasave_int_11",
        "luasave_int_12",
        "luasave_int_13",
        "luasave_int_14",
        "luasave_int_15",
        "luasave_int_16",
        "luasave_int_17",
        "luasave_int_18",
        "luasave_int_19",
        "luasave_int_20",
        "luasave_int_21",
        "luasave_int_22",
        "luasave_int_23",
        "luasave_int_24",
        "luasave_int_25",
        "luasave_int_26",
        "title_d",
        "title_1",
        "title_2",
        "title_3",
        "nofame",
        "yhp",
        "yatk",
        "ydef",
        "yquick",
        "ylv",
        "reserverreward"
    };

    /** String field keys in C table order (index 0..13). */
    public static final String[] STRING_KEYS = {
        "name",
        "ownt",
        "arg",
        "ocd",
        "ocn",
        "fmname",
        "ucode",
        "tsi",
        "tsn",
        "love",
        "lovrrid",
        "lovrrneme",
        "newname",
        "powerup"
    };

    public static final class Save {
        public final Map<String, Integer> ints = new LinkedHashMap<>();
        public final Map<String, String> strings = new LinkedHashMap<>();
        public final Map<Integer, Integer> flags = new LinkedHashMap<>();
        public final List<String> skills = new ArrayList<>();
        public final List<String> items = new ArrayList<>();
        public final List<String> poolItems = new ArrayList<>();
        public final List<Integer> titles = new ArrayList<>();
        public final List<String> addrs = new ArrayList<>();
        public final List<String> pets = new ArrayList<>();
        public final List<String> poolPets = new ArrayList<>();
        public int dataEnd;

        public Save() {
            for (String k : INT_KEYS) ints.put(k, 0);
            for (String k : STRING_KEYS) strings.put(k, "");
        }
    }

    private CharacterSaveCodec() {}

    /** makeStringFromEscaped-compatible: \\ \S \n \r; CJK 2-byte pairs pass through. */
    public static String escape(String s) {
        if (s == null) return "";
        byte[] in = s.getBytes(GBK);
        StringBuilder out = new StringBuilder(in.length + 8);
        for (int i = 0; i < in.length; i++) {
            int b = in[i] & 0xFF;
            if (b >= 0x80) {
                out.append((char) b);
                if (i + 1 < in.length) out.append((char) (in[++i] & 0xFF));
            } else if (b == '\\') out.append("\\\\");
            else if (b == ' ') out.append("\\S");
            else if (b == '\n') out.append("\\n");
            else if (b == '\r') out.append("\\r");
            else out.append((char) b);
        }
        return out.toString();
    }

    public static String descape(String s) {
        if (s == null) return "";
        byte[] in = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder(in.length);
        for (int i = 0; i < in.length; i++) {
            int b = in[i] & 0xFF;
            if (b >= 0x80) {
                out.append((char) b);
                if (i + 1 < in.length) out.append((char) (in[++i] & 0xFF));
            } else if (b == '\\') {
                if (i + 1 >= in.length) { out.append('\\'); break; }
                int n = in[++i] & 0xFF;
                if (n == 'S') out.append(' ');
                else if (n == 'n') out.append('\n');
                else if (n == 'r') out.append('\r');
                else out.append('\\');
            } else out.append((char) b);
        }
        return new String(out.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), GBK);
    }

    /** CHAR_makeStringFromCharData text body (without needing the 128-byte C slots). */
    public static String encode(Save s) {
        StringBuilder sb = new StringBuilder(4096);
        for (String k : INT_KEYS) {
            int v = s.ints.getOrDefault(k, 0);
            sb.append(k).append('=').append(v).append('\n');
        }
        for (String k : STRING_KEYS) {
            String v = s.strings.getOrDefault(k, "");
            sb.append(k).append('=').append(escape(v)).append('\n');
        }
        for (Map.Entry<Integer, Integer> e : s.flags.entrySet()) {
            sb.append("flg").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        for (int i = 0; i < s.skills.size(); i++) {
            sb.append("skill").append(i).append('=').append(escape(s.skills.get(i))).append('\n');
        }
        for (int i = 0; i < s.items.size(); i++) {
            sb.append("item").append(i).append('=').append(escape(s.items.get(i))).append('\n');
        }
        for (int i = 0; i < s.poolItems.size(); i++) {
            sb.append("poolitem").append(i).append('=').append(escape(s.poolItems.get(i))).append('\n');
        }
        for (int i = 0; i < s.titles.size(); i++) {
            sb.append("title").append(i).append('=').append(s.titles.get(i)).append('\n');
        }
        for (int i = 0; i < s.addrs.size(); i++) {
            sb.append("adr").append(i).append('=').append(escape(s.addrs.get(i))).append('\n');
        }
        for (int i = 0; i < s.pets.size(); i++) {
            sb.append("pet").append(i).append('=').append(escape(s.pets.get(i))).append('\n');
        }
        for (int i = 0; i < s.poolPets.size(); i++) {
            sb.append("poolpet").append(i).append('=').append(escape(s.poolPets.get(i))).append('\n');
        }
        sb.append("DATAEND=").append(s.dataEnd).append('\n');
        if (sb.length() > SAVE_BUFFER_LIMIT) {
            throw new IllegalStateException("save buffer over: " + sb.length());
        }
        return sb.toString();
    }

    /** CHAR_makeCharFromStringToArg — parse key=value lines. */
    public static Save decode(String text) {
        Save s = new Save();
        if (text == null || text.isEmpty()) return s;
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq);
            String val = line.substring(eq + 1);
            if (key.equals("DATAEND")) {
                s.dataEnd = parseIntSafe(val);
                continue;
            }
            // exact field tables win over section prefixes (titlelv, title_d, ...)
            if (s.ints.containsKey(key)) {
                s.ints.put(key, parseIntSafe(val));
                continue;
            }
            if (s.strings.containsKey(key)) {
                s.strings.put(key, descape(val));
                continue;
            }
            if (key.startsWith("flg")) {
                s.flags.put(parseIntSafe(key.substring(3)), parseIntSafe(val));
                continue;
            }
            if (key.startsWith("skill")) {
                s.skills.add(descape(val)); // order preserved by sequential add
                continue;
            }
            if (key.startsWith("poolitem")) {
                s.poolItems.add(descape(val));
                continue;
            }
            if (key.startsWith("item")) {
                s.items.add(descape(val));
                continue;
            }
            if (key.startsWith("title")) {
                s.titles.add(parseIntSafe(val));
                continue;
            }
            if (key.startsWith("adr")) {
                s.addrs.add(descape(val));
                continue;
            }
            if (key.startsWith("poolpet")) {
                s.poolPets.add(descape(val));
                continue;
            }
            if (key.startsWith("pet")) {
                s.pets.add(descape(val));
                continue;
            }
        }
        return s;
    }

    private static int parseIntSafe(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
