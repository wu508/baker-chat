package dev.wanye.bakerchat.mixin;

import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.TextRenderLayerSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版字体图集是最近邻采样，TTF 字形放大 oversample 倍再缩回去时会被丢像素，
 * 小字号下就是一片抖动。改成线性过滤，字形边缘才会平滑。
 */
@Mixin(GlyphAtlasTexture.class)
public class GlyphAtlasTextureMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bakerchat$smoothGlyphs(TextRenderLayerSet layers, boolean cull, CallbackInfo ci) {
        ((GlyphAtlasTexture) (Object) this).setFilter(true, false);
    }
}
