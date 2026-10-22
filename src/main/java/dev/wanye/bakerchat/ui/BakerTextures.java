package dev.wanye.bakerchat.ui;

import net.minecraft.util.Identifier;

/** 素材都在 assets/baker/textures/ui/ 下，由 tools/prepare-textures.ps1 生成。 */
public final class BakerTextures {
    private static final String NS = "baker";

    public static final Identifier TITLE_DECO = id("title_deco");
    public static final Identifier HEADER_LEFT = id("header_left");
    public static final Identifier HEADER_RIGHT = id("header_right");
    public static final Identifier LIST_MARKS = id("list_marks");
    public static final Identifier CARD_WATERMARK = id("card_watermark");
    public static final Identifier LINE_DECO = id("line_deco");
    public static final Identifier AVATAR_BG = id("avatar_bg");
    public static final Identifier AVATAR_FRAME = id("avatar_frame");
    public static final Identifier ICON_SEND = id("icon_send");
    public static final Identifier ICON_STICKER = id("icon_sticker");
    public static final Identifier ICON_MORE = id("icon_more");
    public static final Identifier ICON_NEW = id("icon_new");
    public static final Identifier COLORS = id("colors");
    public static final Identifier CORNER_DECO = id("corner_deco");
    public static final Identifier TOP_DECO_RIGHT = id("top_deco_right");
    public static final Identifier BTN_SNS = id("btn_sns");
    public static final Identifier HB_DECO = id("hb_deco");

    private BakerTextures() {
    }

    private static Identifier id(String name) {
        return new Identifier(NS, "textures/ui/" + name + ".png");
    }
}
