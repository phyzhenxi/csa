package csa.item;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemPetCodecSelfTest {
    public static void main(String[] args) {
        int fail = 0;

        fail += eq("item_fields", 40, ItemPetCodec.ITEM_FIELDS.length);
        fail += eq("skill_fields", 2, ItemPetCodec.SKILL_FIELDS.length);
        fail += eq("item0", "id", ItemPetCodec.ITEM_FIELDS[0]);
        fail += eq("item39", "forname", ItemPetCodec.ITEM_FIELDS[39]);

        // item mode0
        Map<String, String> it = new LinkedHashMap<>();
        it.put("id", "100");
        it.put("lv", "5");
        String enc = ItemPetCodec.encodeItem(it, false);
        fail += eq("item_enc", "id=100|lv=5|", enc);
        Map<String, String> dec = ItemPetCodec.decodeItem(enc, false);
        fail += eq("item_id", "100", dec.get("id"));
        fail += eq("item_lv", "5", dec.get("lv"));

        // item mode1
        String enc1 = ItemPetCodec.encodeItem(it, true);
        fail += eq("item_enc1", "id:100,lv:5,", enc1);
        fail += eq("item_id1", "100", ItemPetCodec.decodeItem(enc1, true).get("id"));

        // skill
        fail += eq("skill_enc", "lv=3|id=7|", ItemPetCodec.encodeSkill(3, 7));
        int[] sk = ItemPetCodec.decodeSkill("lv=3|id=7|");
        fail += eq("skill_lv", 3, sk[0]);
        fail += eq("skill_id", 7, sk[1]);

        // pet
        ItemPetCodec.PetString pet = new ItemPetCodec.PetString();
        pet.ints.put("lv", 120);
        pet.ints.put("hp", 3000);
        pet.strings.put("name", "小火龙");
        pet.skills.add(5);
        pet.techSkills.add(2);
        pet.items.add("sword");
        String pe = ItemPetCodec.encodePet(pet);
        fail += ok("pet_has_lv", pe.contains("lv:120|"));
        fail += ok("pet_has_psk", pe.contains("psk0:5|"));
        fail += ok("pet_has_ptsk", pe.contains("ptsk0:2|"));
        fail += ok("pet_has_pitem", pe.contains("pitem0:sword|"));

        ItemPetCodec.PetString pr = ItemPetCodec.decodePet(pe);
        fail += eq("pet_lv", 120, pr.ints.get("lv"));
        fail += eq("pet_hp", 3000, pr.ints.get("hp"));
        fail += eq("pet_name", "小火龙", pr.strings.get("name"));
        fail += eq("pet_psk", 5, (int) pr.skills.get(0));
        fail += eq("pet_pitem", "sword", pr.items.get(0));

        System.out.println(fail == 0 ? "ITEM PET ALL PASS" : "ITEM PET FAIL " + fail);
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