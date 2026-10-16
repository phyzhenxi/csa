package csa.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nested item / pet / skill string codecs recovered from gmsvjt.
 *
 * ITEM_makeStringFromItemData @ 0x81C36DE
 *   mode0: "key=val|key=val|"   kv='=' pair='|'
 *   mode1: "key:val,key:val,"   kv=':' pair=','
 *   format "%s%c%d%c" / "%s%c%s%c", buffer 0x8401480 / 0x1000
 *
 * CHAR_makePetStringFromPetIndex @ 0x80B437F
 *   "key:value|"  int fields x289 (same names as character)
 *   "key:value|"  string fields
 *   "psk%d:%d|"   pet skills
 *   "ptsk%d:%d|"  pet tech skills
 *   "pitem%d:%s|" pet items
 *   buffer 0x83667C0 / 0x2000
 *
 * SKILL_makeStringFromSkillData @ 0x80DDFA8
 *   "lv=%d|id=%d|" then trailing '|' trimmed
 */
public final class ItemPetCodec {

    public static final char KV0 = '=';
    public static final char PAIR0 = '|';
    public static final char KV1 = ':';
    public static final char PAIR1 = ',';

    /** Item int/string field names (table @ 0x832FDA0, 16-byte records). */
    public static final String[] ITEM_FIELDS = {
        "id","bi","tg","dib","upin","dmce","mdmce","ann","anx","ma",
        "md","mh","mm","mq","ml","mc","mv","mat","mav","mid",
        "mpr","mu","ll","mrf","npo","npa","nsl","nst","ndr","nco",
        "coloer","itime",
        // string-side (indices restart at 0)
        "na","sn","en","ar","acode","inlaycode","cdk","forname"
    };

    /** Skill fields @ 0x82C4620. */
    public static final String[] SKILL_FIELDS = { "lv", "id" };

    private ItemPetCodec() {}

    // ---------------------------------------------------------------- item

    /** Encode item fields. mode0 uses = and |. */
    public static String encodeItem(Map<String, String> fields, boolean mode1) {
        char kv = mode1 ? KV1 : KV0;
        char pair = mode1 ? PAIR1 : PAIR0;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append(e.getKey()).append(kv).append(e.getValue()).append(pair);
        }
        return sb.toString();
    }

    public static Map<String, String> decodeItem(String s, boolean mode1) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) return out;
        char kv = mode1 ? KV1 : KV0;
        char pair = mode1 ? PAIR1 : PAIR0;
        for (String part : s.split(java.util.regex.Pattern.quote(String.valueOf(pair)))) {
            if (part.isEmpty()) continue;
            int i = part.indexOf(kv);
            if (i <= 0) continue;
            out.put(part.substring(0, i), part.substring(i + 1));
        }
        return out;
    }

    // ---------------------------------------------------------------- skill

    /** "lv=N|id=M|" (C also trims trailing '|'). */
    public static String encodeSkill(int lv, int id) {
        return "lv=" + lv + "|id=" + id + "|";
    }

    public static int[] decodeSkill(String s) {
        int lv = 0, id = 0;
        if (s == null) return new int[] {0, 0};
        for (String part : s.split("\\|")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf('=');
            if (i <= 0) continue;
            String k = part.substring(0, i);
            int v = parseInt(part.substring(i + 1));
            if (k.equals("lv")) lv = v;
            else if (k.equals("id")) id = v;
        }
        return new int[] {lv, id};
    }

    // ---------------------------------------------------------------- pet

    public static final class PetString {
        public final Map<String, Integer> ints = new LinkedHashMap<>();
        public final Map<String, String> strings = new LinkedHashMap<>();
        public final List<Integer> skills = new ArrayList<>();      // psk
        public final List<Integer> techSkills = new ArrayList<>();  // ptsk
        public final List<String> items = new ArrayList<>();        // pitem
    }

    /** Pet wire: key:value| for both ints and strings, plus psk/ptsk/pitem. */
    public static String encodePet(PetString p) {
        StringBuilder sb = new StringBuilder(2048);
        for (Map.Entry<String, Integer> e : p.ints.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('|');
        }
        for (Map.Entry<String, String> e : p.strings.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('|');
        }
        for (int i = 0; i < p.skills.size(); i++) {
            sb.append("psk").append(i).append(':').append(p.skills.get(i)).append('|');
        }
        for (int i = 0; i < p.techSkills.size(); i++) {
            sb.append("ptsk").append(i).append(':').append(p.techSkills.get(i)).append('|');
        }
        for (int i = 0; i < p.items.size(); i++) {
            sb.append("pitem").append(i).append(':').append(p.items.get(i)).append('|');
        }
        return sb.toString();
    }

    public static PetString decodePet(String s) {
        PetString p = new PetString();
        if (s == null || s.isEmpty()) return p;
        for (String part : s.split("\\|")) {
            if (part.isEmpty()) continue;
            int i = part.indexOf(':');
            if (i <= 0) continue;
            String k = part.substring(0, i);
            String v = part.substring(i + 1);
            if (k.startsWith("psk")) {
                p.skills.add(parseInt(v));
            } else if (k.startsWith("ptsk")) {
                p.techSkills.add(parseInt(v));
            } else if (k.startsWith("pitem")) {
                p.items.add(v);
            } else if (k.contains("=") ) {
                // defensive
            } else {
                // numeric fields use same names as character ints; value is int
                try {
                    p.ints.put(k, Integer.parseInt(v.trim()));
                } catch (NumberFormatException ex) {
                    p.strings.put(k, v);
                }
            }
        }
        return p;
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
