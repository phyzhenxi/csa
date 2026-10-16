package csa.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory domain snapshot assembled from CharacterSaveCodec / ItemPetCodec.
 * Ready to plug into LssGateway persistence hooks.
 */
public final class Domain {

    public static final class Character {
        public String name = "";
        public String account = "";
        public final Map<String, Integer> ints = new LinkedHashMap<>();
        public final Map<String, String> strings = new LinkedHashMap<>();
        public final List<String> items = new ArrayList<>();
        public final List<String> pets = new ArrayList<>();
        public final List<String> skills = new ArrayList<>();
        public int x, y, dir, floor;
    }

    public static final class Item {
        public final Map<String, String> fields = new LinkedHashMap<>();
    }

    public static final class Pet {
        public String name = "";
        public final Map<String, Integer> ints = new LinkedHashMap<>();
        public final List<Integer> skills = new ArrayList<>();
        public final List<String> items = new ArrayList<>();
    }

    public static final class Account {
        public String cdkey = "";
        public String password = "";
        public int vipPoint;
        public int payPoint;
    }

    private Domain() {}
}