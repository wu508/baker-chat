package dev.wanye.bakerchat;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 聊天的本地副本。vanilla 的聊天记录只留 100 条且随时会被清掉，
 * 这里单独存一份，面板关闭后也还在。
 */
public final class ChatStore {
    public enum Kind {
        /** 玩家发言，带发送者。 */
        CHAT,
        /** 系统消息：加入/退出、服务器公告、指令回执。 */
        SYSTEM,
        /** 只在本地出现的分隔线，不会发给服务器。 */
        DIVIDER,
        /** 贴纸：本地插入的一张图，同样不会发给服务器。 */
        STICKER
    }

    public static final class Entry {
        public final Kind kind;
        public final UUID sender;
        public final String senderName;
        /** 气泡里显示的内容，已经去掉 "<名字>" 前缀。 */
        public final Text text;
        public final String plain;
        /** 贴纸的资源名，只有 STICKER 才有值。 */
        public final String sticker;
        public final long time;

        Entry(Kind kind, UUID sender, String senderName, Text text, String sticker, long time) {
            this.kind = kind;
            this.sender = sender;
            this.senderName = senderName;
            this.text = text;
            this.plain = text.getString();
            this.sticker = sticker;
            this.time = time;
        }
    }

    private static final int MAX_ENTRIES = 400;

    /** 只有主线程读写，加锁是为了将来可能从别的线程塞消息。 */
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static int revision;

    /** 记录落盘的位置和当前所在的服务器，换服就换一份记录。 */
    private static java.nio.file.Path storeFile;
    private static String serverKey = "";
    private static boolean dirty;

    private ChatStore() {
    }

    public static synchronized void init(java.nio.file.Path configDir) {
        storeFile = configDir.resolve("baker-chat-history.json");
    }

    /** 进入世界/服务器时调用：换了地方就把上一份记录清掉。 */
    public static synchronized void enterWorld(String key) {
        if (key.equals(serverKey)) {
            return;
        }

        serverKey = key;
        ENTRIES.clear();
        revision++;
        load();
    }

    public static synchronized void flush() {
        if (!dirty || storeFile == null) {
            return;
        }
        dirty = false;

        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("server", serverKey);

        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (Entry entry : ENTRIES) {
            com.google.gson.JsonObject item = new com.google.gson.JsonObject();
            item.addProperty("kind", entry.kind.name());
            item.addProperty("text", entry.plain);
            item.addProperty("time", entry.time);
            if (entry.sender != null) {
                item.addProperty("sender", entry.sender.toString());
            }
            if (entry.senderName != null) {
                item.addProperty("name", entry.senderName);
            }
            if (entry.sticker != null) {
                item.addProperty("sticker", entry.sticker);
            }
            array.add(item);
        }
        root.add("entries", array);

        try {
            java.nio.file.Files.createDirectories(storeFile.getParent());
            java.nio.file.Files.writeString(storeFile, root.toString());
        } catch (Exception ignored) {
            // 写不进去就算了，不能让聊天因为存档失败而崩
        }
    }

    private static void load() {
        if (storeFile == null || !java.nio.file.Files.exists(storeFile)) {
            return;
        }

        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(storeFile)).getAsJsonObject();
            if (!serverKey.equals(root.has("server") ? root.get("server").getAsString() : "")) {
                return;
            }

            for (com.google.gson.JsonElement element : root.getAsJsonArray("entries")) {
                com.google.gson.JsonObject item = element.getAsJsonObject();
                Kind kind;
                try {
                    kind = Kind.valueOf(item.get("kind").getAsString());
                } catch (Exception e) {
                    kind = Kind.SYSTEM;
                }

                String text = item.has("text") ? item.get("text").getAsString() : "";
                String sticker = item.has("sticker") ? item.get("sticker").getAsString() : null;
                UUID sender = item.has("sender") ? UUID.fromString(item.get("sender").getAsString()) : null;
                String name = item.has("name") ? item.get("name").getAsString() : null;
                long time = item.has("time") ? item.get("time").getAsLong() : System.currentTimeMillis();

                ENTRIES.add(new Entry(kind, sender, name, Text.literal(text), sticker, time));
            }

            trim();
            revision++;
        } catch (Exception ignored) {
            // 文件坏了就当没有
        }
    }

    private static void markDirty() {
        dirty = true;
    }

    public static synchronized void addChat(UUID sender, String senderName, Text text) {
        String stripped = stripSpeaker(text.getString(), senderName);
        Text body = stripped.equals(text.getString()) ? text : Text.literal(stripped);
        ENTRIES.add(new Entry(Kind.CHAT, sender, senderName, body, null, System.currentTimeMillis()));
        trim();
        revision++;
        markDirty();
    }

    public static synchronized void addSystem(Text text) {
        ENTRIES.add(new Entry(Kind.SYSTEM, null, null, text, null, System.currentTimeMillis()));
        trim();
        revision++;
        markDirty();
    }

    /** 本地分隔线，只影响面板里的排版。 */
    public static synchronized void addDivider() {
        ENTRIES.add(new Entry(Kind.DIVIDER, null, null, Text.empty(), null, System.currentTimeMillis()));
        trim();
        revision++;
        markDirty();
    }

    /** 本地贴纸：插在会话里的一张图，不会发给服务器。 */
    public static synchronized void addSticker(String name) {
        ENTRIES.add(new Entry(Kind.STICKER, null, null, Text.empty(), name, System.currentTimeMillis()));
        trim();
        revision++;
        markDirty();
    }

    public static synchronized List<Entry> snapshot() {
        return List.copyOf(ENTRIES);
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        revision++;
        markDirty();
        flush();
    }

    private static void trim() {
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
    }

    /**
     * vanilla 的聊天是 "&lt;名字&gt; 正文"，Baker 的气泡里只有正文，头像负责显示是谁。
     * 认不出来的格式（带称号前缀之类）就原样保留。
     */
    private static String stripSpeaker(String plain, String senderName) {
        if (senderName == null || senderName.isEmpty()) {
            return plain;
        }

        String head = "<" + senderName + ">";
        if (plain.startsWith(head)) {
            String rest = plain.substring(head.length());
            return rest.startsWith(" ") ? rest.substring(1) : rest;
        }

        return plain;
    }
}
