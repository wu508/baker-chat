package dev.wanye.bakerchat.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把 ":smile:" 这种标记当图片排进文字里，排版自己做。
 * Baker 的正文里表情是内嵌的，用原版 wrapLines 没法插图片，所以这里自己折行。
 */
public final class EmojiText {
    public static final int EMOJI_SIZE = 10;
    public static final int LINE_HEIGHT = 10;

    /** 一段内容：文字（带字体样式）或一张表情。 */
    public record Piece(String text, Text styled, Identifier emoji, int width) {
    }

    private EmojiText() {
    }

    public static List<List<Piece>> layout(TextRenderer font, String raw, int maxWidth) {
        List<List<Piece>> lines = new ArrayList<>();
        List<Piece> line = new ArrayList<>();
        int lineWidth = 0;
        int i = 0;

        while (i < raw.length()) {
            int emojiEnd = raw.charAt(i) == ':' ? raw.indexOf(':', i + 1) : -1;
            if (emojiEnd > i + 1 && emojiEnd - i - 1 <= 24) {
                int index = BakerEmoji.indexOf(raw.substring(i + 1, emojiEnd).toLowerCase(Locale.ROOT));
                if (index >= 0) {
                    if (lineWidth + EMOJI_SIZE > maxWidth && !line.isEmpty()) {
                        lines.add(line);
                        line = new ArrayList<>();
                        lineWidth = 0;
                    }
                    line.add(new Piece(null, null, BakerEmoji.texture(index), EMOJI_SIZE));
                    lineWidth += EMOJI_SIZE;
                    i = emojiEnd + 1;
                    continue;
                }
            }

            char c = raw.charAt(i);
            if (c == '\n') {
                lines.add(line);
                line = new ArrayList<>();
                lineWidth = 0;
                i++;
                continue;
            }

            int charWidth = width(font, String.valueOf(c));
            if (lineWidth + charWidth > maxWidth && !line.isEmpty()) {
                lines.add(line);
                line = new ArrayList<>();
                lineWidth = 0;
            }

            if (!line.isEmpty() && line.get(line.size() - 1).emoji() == null) {
                Piece last = line.get(line.size() - 1);
                String merged = last.text() + c;
                line.set(line.size() - 1, new Piece(merged, styled(merged), null, last.width() + charWidth));
            } else {
                String single = String.valueOf(c);
                line.add(new Piece(single, styled(single), null, charWidth));
            }

            lineWidth += charWidth;
            i++;
        }

        lines.add(line);
        return lines;
    }

    private static Text styled(String value) {
        return Text.literal(value).setStyle(BakerFont.STYLE);
    }

    private static int width(TextRenderer font, String value) {
        return font.getWidth(styled(value));
    }

    public static int lineWidth(List<Piece> line) {
        int width = 0;
        for (Piece piece : line) {
            width += piece.width();
        }
        return width;
    }

    public static void draw(DrawContext context, TextRenderer font, List<List<Piece>> lines, int x, int y, int color) {
        int lineY = y;

        for (List<Piece> line : lines) {
            int cursor = x;
            for (Piece piece : line) {
                if (piece.emoji() != null) {
                    BakerDraw.texScaled(context, piece.emoji(), cursor, lineY, EMOJI_SIZE, EMOJI_SIZE,
                            0.0f, 0.0f, 24, 24, 24, 24);
                } else {
                    context.drawText(font, piece.styled(), cursor, lineY, color, false);
                }
                cursor += piece.width();
            }
            lineY += LINE_HEIGHT;
        }
    }
}
