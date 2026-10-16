package csa.db;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import csa.save.CharacterSaveCodec;

/**
 * Self-test: write/load round-trip for user1/0 via CharacterSaveCodec.
 * Prints CHARSAVE STORE PASS on success.
 */
public final class CharSaveStoreSelfTest {

    public static void main(String[] args) throws Exception {
        int fail = 0;

        CharacterSaveCodec.Save s = new CharacterSaveCodec.Save();
        s.ints.put("lv", 120);
        s.strings.put("name", "勇者A");
        s.dataEnd = 1;
        String text = CharacterSaveCodec.encode(s);

        Path root = CharSaveStore.DEFAULT_ROOT;
        CharSaveStore store = new CharSaveStore(root);

        store.save("user1", 0, text);
        fail += ok("file_exists", Files.isRegularFile(root.resolve("user1").resolve("0.save")));
        fail += ok("sidecar_exists", Files.isRegularFile(root.resolve("user1").resolve("0.save.json")));

        String loaded = store.load("user1", 0);
        fail += ok("rt_text", text != null && text.equals(loaded));
        fail += eq("rt_len", text.length(), loaded == null ? -1 : loaded.length());

        CharacterSaveCodec.Save r = CharacterSaveCodec.decode(loaded);
        fail += eq("rt_lv", 120, r.ints.get("lv"));
        fail += eq("rt_name", "勇者A", r.strings.get("name"));

        List<Integer> list = store.list("user1");
        fail += ok("list_has_0", list.contains(0));
        fail += eq("list_size", 1, list.size());

        String side = new String(
                Files.readAllBytes(Paths.get(root.toString(), "user1", "0.save.json")),
                StandardCharsets.UTF_8);
        fail += ok("side_name", side.contains("\"name\": \"勇者A\""));
        fail += ok("side_level", side.contains("\"level\": 120"));

        fail += eq("load_missing", null, store.load("user1", 99));

        System.out.println(fail == 0 ? "CHARSAVE STORE PASS" : "CHARSAVE STORE FAIL " + fail);
        if (fail != 0) {
            System.exit(1);
        }
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
