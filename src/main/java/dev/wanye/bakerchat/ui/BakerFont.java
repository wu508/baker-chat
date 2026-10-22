package dev.wanye.bakerchat.ui;

import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

/** 面板自己绘制文字时用的字体：Noto Sans SC，缺字回落到原版字体。 */
public final class BakerFont {
    public static final Identifier ID = new Identifier("baker", "baker");
    public static final Style STYLE = Style.EMPTY.withFont(ID);

    private BakerFont() {
    }
}
