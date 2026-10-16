package csa.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import csa.save.CharacterSaveCodec;

/**
 * File-backed full-save blob store for CharLogin delivery.
 *
 * Layout: {root}/{cdkey}/{saveIndex}.save          — CharacterSaveCodec text
 *         {root}/{cdkey}/{saveIndex}.save.json     — name/level/meta sidecar
 *
 * Prefer file store over a DB table (no ALTER permission assumed).
 */
public final class CharSaveStore {

    public static final Path DEFAULT_ROOT = Paths.get("E:/REA/csa-rea-artifacts/data/saves");

    private final Path root;

    public CharSaveStore() {
        this(DEFAULT_ROOT);
    }

    public CharSaveStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Persist full save TEXT and a JSON sidecar (name/level/meta). */
    public void save(String cdkey, int saveIndex, String saveText) throws IOException {
        if (saveText == null) {
            throw new IllegalArgumentException("saveText is null");
        }
        Path file = saveFile(cdkey, saveIndex);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, saveText.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        writeSidecar(cdkey, saveIndex, saveText, file);
    }

    /** Load full save TEXT, or null if absent. */
    public String load(String cdkey, int saveIndex) throws IOException {
        Path file = saveFile(cdkey, saveIndex);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** Save indexes present for cdkey, ascending. */
    public List<Integer> list(String cdkey) throws IOException {
        Path dir = cdkeyDir(cdkey);
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Integer> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.save")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                String stem = name.substring(0, name.length() - ".save".length());
                try {
                    out.add(Integer.valueOf(stem));
                } catch (NumberFormatException ignore) {
                    // skip non-index files
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    private Path cdkeyDir(String cdkey) {
        String key = requireSafeToken(cdkey, "cdkey");
        return root.resolve(key);
    }

    private Path saveFile(String cdkey, int saveIndex) {
        if (saveIndex < 0) {
            throw new IllegalArgumentException("saveIndex < 0: " + saveIndex);
        }
        return cdkeyDir(cdkey).resolve(saveIndex + ".save");
    }

    private static String requireSafeToken(String value, String what) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(what + " empty");
        }
        if (value.equals(".") || value.equals("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(what + " illegal: " + value);
        }
        return value;
    }

    private void writeSidecar(String cdkey, int saveIndex, String saveText, Path saveFile) throws IOException {
        String name = "";
        int level = 0;
        try {
            CharacterSaveCodec.Save s = CharacterSaveCodec.decode(saveText);
            if (s.strings.get("name") != null) {
                name = s.strings.get("name");
            }
            Integer lv = s.ints.get("lv");
            if (lv != null) {
                level = lv.intValue();
            }
        } catch (RuntimeException ignore) {
            // keep empty meta on undecodable blobs
        }
        String json = "{\n"
                + "  \"cdkey\": " + jsonStr(cdkey) + ",\n"
                + "  \"saveIndex\": " + saveIndex + ",\n"
                + "  \"name\": " + jsonStr(name) + ",\n"
                + "  \"level\": " + level + ",\n"
                + "  \"bytes\": " + saveText.getBytes(StandardCharsets.UTF_8).length + ",\n"
                + "  \"savedAt\": " + jsonStr(java.time.Instant.now().toString()) + "\n"
                + "}\n";
        Path side = Paths.get(saveFile.toString() + ".json");
        Path tmp = side.resolveSibling(side.getFileName() + ".tmp");
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tmp, side, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String jsonStr(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
