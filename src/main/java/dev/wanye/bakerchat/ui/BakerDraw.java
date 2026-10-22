package dev.wanye.bakerchat.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 画圆角、贴图、描边这些 Baker 界面反复用到的小工具。 */
public final class BakerDraw {
    private BakerDraw() {
    }

    /** 用面板字体画一行字。 */
    public static void text(DrawContext ctx, TextRenderer font, String value, int x, int y, int color) {
        ctx.drawText(font, Text.literal(value).setStyle(BakerFont.STYLE), x, y, color, false);
    }

    public static int textWidth(TextRenderer font, String value) {
        return font.getWidth(Text.literal(value).setStyle(BakerFont.STYLE));
    }

    /** 四角都是圆的矩形。 */
    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        roundRect(ctx, x, y, w, h, r, color, true, true);
    }

    /**
     * 只圆某几边的矩形。header 只圆上面、输入栏只圆下面，和 Baker 的 CSS 一致。
     *
     * 实际绘制放大一倍再缩回去：GUI 是按整数倍缩放的，直接按逻辑像素画圆角，
     * 一格就是 2~3 个屏幕像素；放大一倍相当于给形状做 2x 超采样。
     */
    public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }

        MatrixStack matrices = ctx.getMatrices();
        matrices.push();
        matrices.scale(0.5f, 0.5f, 1.0f);
        roundRectRaw(ctx, x * 2, y * 2, w * 2, h * 2, r * 2, color, top, bottom);
        matrices.pop();
    }

    private static void roundRectRaw(DrawContext ctx, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }

        int sideInset = top || bottom ? r : 0;
        ctx.fill(x + sideInset, y, x + w - sideInset, y + h, color);

        if (top && bottom) {
            ctx.fill(x, y + r, x + r, y + h - r, color);
            ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        } else if (top) {
            ctx.fill(x, y + r, x + w, y + h, color);
        } else {
            ctx.fill(x, y, x + w, y + h - r, color);
        }

        for (int i = 0; i < r; i++) {
            double boundary = cornerBoundary(i, r);
            int inset = (int) Math.ceil(boundary);
            // 边界那一像素被覆盖了多少，用它做透明度，边缘就不会是硬台阶
            int coverage = (int) Math.round(Math.min(1.0, Math.max(0.0, inset - boundary)) * 255);
            int edge = withAlpha(color, coverage);

            if (top) {
                ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
                if (inset > 0 && coverage > 0) {
                    ctx.fill(x + inset - 1, y + i, x + inset, y + i + 1, edge);
                    ctx.fill(x + w - inset, y + i, x + w - inset + 1, y + i + 1, edge);
                }
            }
            if (bottom) {
                ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
                if (inset > 0 && coverage > 0) {
                    ctx.fill(x + inset - 1, y + h - 1 - i, x + inset, y + h - i, edge);
                    ctx.fill(x + w - inset, y + h - 1 - i, x + w - inset + 1, y + h - i, edge);
                }
            }
        }
    }

    /** 带 1px 描边的圆角矩形：先画外圈再画内填充，避免方角压在圆角上。 */
    public static void roundRectBorder(DrawContext ctx, int x, int y, int w, int h, int r, int border, int fill) {
        roundRect(ctx, x, y, w, h, r, border);
        roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), fill);
    }

    public static void roundRectBorder(DrawContext ctx, int x, int y, int w, int h, int r, int border, int fill,
                                       boolean top, boolean bottom) {
        roundRect(ctx, x, y, w, h, r, border, top, bottom);
        int insetY = top ? 1 : 0;
        int insetH = h - (top ? 1 : 0) - (bottom ? 1 : 0);
        roundRect(ctx, x + 1, y + insetY, w - 2, insetH, Math.max(1, r - 1), fill, top, bottom);
    }

    /** 第 row 行圆角边界的精确位置（相对矩形左边），用小数是为了算覆盖率。 */
    private static double cornerBoundary(int row, int radius) {
        double dy = radius - row - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return radius - dx;
    }

    private static int withAlpha(int color, int alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int blended = Math.min(baseAlpha, alpha);
        return (color & 0x00FFFFFF) | (blended << 24);
    }

    public static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** 1:1 画贴图，调用方保证目标尺寸和原图一致。 */
    public static void tex(DrawContext ctx, Identifier texture, int x, int y, int w, int h, int texW, int texH) {
        ctx.drawTexture(texture, x, y, 0, 0, w, h, texW, texH);
    }

    /** 缩放画贴图：宽高是目标尺寸，u/v 是源图像素坐标，region 是源图裁剪大小。 */
    public static void texScaled(DrawContext ctx, Identifier texture, int x, int y, int w, int h,
                                 float u, float v, int regionW, int regionH, int texW, int texH) {
        ctx.drawTexture(texture, x, y, w, h, u, v, regionW, regionH, texW, texH);
    }

    /** 玩家皮肤的脸：从 64x64 的 (8,8) 取 8x8 的头。 */
    public static void face(DrawContext ctx, Identifier skin, int x, int y, int size) {
        ctx.drawTexture(skin, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
    }
}
