package dev.wanye.bakerchat.ui;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Baker 的 38 个表情。名字取自 baker-dx 里 Emoji 枚举的 lowercase 形式，
 * 顺序和 sns_emoji_001..038.png 一一对应。
 */
public final class BakerEmoji {
    public static final String[] NAMES = {
            "smile", "stareye", "surprise", "smug", "thumb", "please", "lol", "cry", "unwell", "sweat",
            "cool", "playful", "confused", "sorry", "pray", "ok", "tongue", "love", "blush", "joy",
            "heart", "sparkle", "sweatsmile", "laugh", "plusone", "sideeye", "hundred", "dead", "anger", "angry",
            "dizzy", "scream", "sad", "sleep", "doubt", "speechless", "fistbump", "think"
    };

    private static final Map<String, Integer> INDEX = new HashMap<>();

    static {
        for (int i = 0; i < NAMES.length; i++) {
            INDEX.put(NAMES[i], i);
        }
    }

    private BakerEmoji() {
    }

    public static int size() {
        return NAMES.length;
    }

    public static int indexOf(String name) {
        Integer index = INDEX.get(name);
        return index == null ? -1 : index;
    }

    public static Identifier texture(int index) {
        return new Identifier("baker", String.format("textures/ui/emoji/sns_emoji_%03d.png", index + 1));
    }
}
