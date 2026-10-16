package csa.save;

/**
 * Character save field keys recovered from gmsvjt symbol tables:
 *   int table    @ 0x8317DBC  (indices 1..289)
 *   string table @ 0x8318260  (indices 0..13)
 *
 * Serialization: CHAR_makeStringFromCharData @ 0x80B4EEB
 *   int:    "%s=%d\n"
 *   string: "%s=%s\n"  (escaped; 128-byte slots at char+0x488)
 *   flags:  "flg%d=%d\n" (char+0xB88)
 *   then skill/item/poolitem/title/adr/pet/poolpet sections
 *   terminator: "DATAEND=%d\n"
 *
 * Full machine-readable table: data/character-fields.json
 */
public final class CharacterFields {

    public static final int INT_FIELD_COUNT = 289;
    public static final int STRING_FIELD_COUNT = 14;
    public static final int SAVE_BUFFER_LIMIT = 0x20000; // 131072

    // ---- string fields (index order) ----
    public static final String STR_NAME = "name";
    public static final String STR_OWNt = "ownt";
    public static final String STR_ARG = "arg";
    public static final String STR_OCD = "ocd";
    public static final String STR_OCN = "ocn";
    public static final String STR_FMNAME = "fmname";
    public static final String STR_UCODE = "ucode";
    public static final String STR_TSI = "tsi";
    public static final String STR_TSN = "tsn";
    public static final String STR_LOVE = "love";
    public static final String STR_LOVRRID = "lovrrid";
    public static final String STR_LOVRRNEME = "lovrrneme";
    public static final String STR_NEWNAME = "newname";
    public static final String STR_POWERUP = "powerup";

    // ---- core int fields (index → key) ----
    public static final String INT_PN = "pn";
    public static final String INT_BI = "bi";
    public static final String INT_BBI = "bbi";
    public static final String INT_FB = "fb";
    public static final String INT_FL = "fl";
    public static final String INT_X = "x";
    public static final String INT_Y = "y";
    public static final String INT_DIR = "dir";
    public static final String INT_LV = "lv";
    public static final String INT_GLD = "gld";
    public static final String INT_HP = "hp";
    public static final String INT_MP = "mp";
    public static final String INT_MMP = "mmp";
    public static final String INT_VI = "vi";
    public static final String INT_STR = "str";
    public static final String INT_TOU = "tou";
    public static final String INT_DX = "dx";
    public static final String INT_CHR = "chr";
    public static final String INT_LUC = "luc";
    public static final String INT_AEA = "aea";
    public static final String INT_AWA = "awa";
    public static final String INT_AFI = "afi";
    public static final String INT_AWI = "awi";

    /** Element order used by battle attribute code: earth, water, fire, wind. */
    public static final String[] ELEMENT_KEYS = { INT_AEA, INT_AWA, INT_AFI, INT_AWI };

    // section markers in save text
    public static final String MARKER_DATAEND = "DATAEND";
    public static final String MARKER_FLG = "flg";
    public static final String MARKER_SKILL = "skill";
    public static final String MARKER_ITEM = "item";
    public static final String MARKER_POOLITEM = "poolitem";
    public static final String MARKER_TITLE = "title";
    public static final String MARKER_ADR = "adr";
    public static final String MARKER_PET = "pet";
    public static final String MARKER_POOLPET = "poolpet";

    private CharacterFields() {}
}
