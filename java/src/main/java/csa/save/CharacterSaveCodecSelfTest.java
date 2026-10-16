package csa.save;

import java.util.Map;

/** Golden tests for CharacterSaveCodec — 289+14 fields, round-trip, escape. */
public final class CharacterSaveCodecSelfTest {

    public static void main(String[] args) {
        int fail = 0;

        fail += eq("int_count", 289, CharacterSaveCodec.INT_KEYS.length);
        fail += eq("str_count", 14, CharacterSaveCodec.STRING_KEYS.length);
        fail += eq("int0", "pn", CharacterSaveCodec.INT_KEYS[0]);
        fail += eq("int288", "reserverreward", CharacterSaveCodec.INT_KEYS[288]);
        fail += eq("str0", "name", CharacterSaveCodec.STRING_KEYS[0]);
        fail += eq("str13", "powerup", CharacterSaveCodec.STRING_KEYS[13]);

        // escape
        fail += eq("esc", "\\S", CharacterSaveCodec.escape(" "));
        fail += eq("unescape", " ", CharacterSaveCodec.descape("\\S"));
        fail += eq("esc_cjk_len", 2, CharacterSaveCodec.escape("石").length());
        fail += eq("unescape_cjk", "石", CharacterSaveCodec.descape(CharacterSaveCodec.escape("石")));

        // round-trip
        CharacterSaveCodec.Save s = new CharacterSaveCodec.Save();
        s.ints.put("lv", 140);
        s.ints.put("gld", 999999);
        s.ints.put("hp", 3000);
        s.ints.put("aea", 25);
        s.ints.put("awa", 25);
        s.ints.put("afi", 25);
        s.ints.put("awi", 25);
        s.strings.put("name", "测试勇者");
        s.strings.put("fmname", "测试 家族");
        s.flags.put(0, 1);
        s.flags.put(3, 7);
        s.skills.add("skillA");
        s.items.add("item B");
        s.poolItems.add("poolC");
        s.titles.add(5);
        s.addrs.add("addrD");
        s.pets.add("pet E");
        s.poolPets.add("poolF");
        s.dataEnd = 42;

        String text = CharacterSaveCodec.encode(s);
        fail += ok("has_lv", text.contains("lv=140\n"));
        fail += ok("has_name", text.contains("name="));
        fail += ok("has_flg", text.contains("flg0=1\n"));
        fail += ok("has_end", text.contains("DATAEND=42\n"));
        fail += ok("has_item_sp", text.contains("item0="));

        CharacterSaveCodec.Save r = CharacterSaveCodec.decode(text);
        fail += eq("rt_lv", 140, r.ints.get("lv"));
        fail += eq("rt_gld", 999999, r.ints.get("gld"));
        fail += eq("rt_name", "测试勇者", r.strings.get("name"));
        fail += eq("rt_fmname", "测试 家族", r.strings.get("fmname"));
        fail += eq("rt_flg0", 1, r.flags.get(0));
        fail += eq("rt_flg3", 7, r.flags.get(3));
        fail += eq("rt_dataEnd", 42, r.dataEnd);
        fail += eq("rt_items", 1, r.items.size());
        fail += eq("rt_item0", "item B", r.items.get(0));
        fail += eq("rt_pet0", "pet E", r.pets.get(0));
        fail += eq("rt_pets", 1, r.pets.size());

        // all int keys survive
        CharacterSaveCodec.Save s2 = new CharacterSaveCodec.Save();
        for (int i = 0; i < 289; i++) {
            s2.ints.put(CharacterSaveCodec.INT_KEYS[i], i + 1);
        }
        CharacterSaveCodec.Save r2 = CharacterSaveCodec.decode(CharacterSaveCodec.encode(s2));
        int mismatch = 0;
        for (int i = 0; i < 289; i++) {
            if (r2.ints.get(CharacterSaveCodec.INT_KEYS[i]) != i + 1) mismatch++;
        }
        fail += eq("rt_all_ints", 0, mismatch);

        // buffer limit guard
        try {
            CharacterSaveCodec.Save big = new CharacterSaveCodec.Save();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 300; i++) sb.append('x');
            big.strings.put("name", sb.toString());
            // 300 chars is fine; force overflow via many pets
            for (int i = 0; i < 4000; i++) big.pets.add("pet-with-padding-" + i + "-" + "x".repeat(40));
            CharacterSaveCodec.encode(big);
            fail += ok("overflow", false); // should have thrown
        } catch (IllegalStateException e) {
            fail += ok("overflow", true);
        }

        System.out.println(fail == 0 ? "SAVE CODEC ALL PASS" : "SAVE CODEC FAIL " + fail);
        if (fail != 0) System.exit(1);
    }

    static int eq(String n, Object a, Object b) {
        boolean ok = String.valueOf(a).equals(String.valueOf(b));
        System.out.println((ok ? "ok   " : "FAIL ") + n + " expect=" + a + " actual=" + b);
        return ok ? 0 : 1;
    }

    static int ok(String n, boolean c) {
        System.out.println((c ? "ok   " : "FAIL ") + n);
        return c ? 0 : 1;
    }
}