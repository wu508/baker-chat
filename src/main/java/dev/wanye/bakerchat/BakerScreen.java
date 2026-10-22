package dev.wanye.bakerchat;

import dev.wanye.bakerchat.ui.BakerDraw;
import dev.wanye.bakerchat.ui.BakerEmoji;
import dev.wanye.bakerchat.ui.BakerTextures;
import dev.wanye.bakerchat.ui.EmojiText;
import dev.wanye.bakerchat.ui.Kaomoji;
import dev.wanye.bakerchat.ui.Stickers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 全屏的 Baker 面板：左边会话列表，右边消息，底部输入栏。
 * 几何尺寸都按 Screen 的逻辑坐标算，所以 GUI 缩放改变时会自动重排。
 */
public class BakerScreen extends Screen {
    private static final Text TITLE = Text.literal("//BAKER/会话消息");
    private static final String EMPTY = "这里还没有消息";
    private static final String PLACEHOLDER = "发送消息…";

    private static final int CARD_H = 34;
    private static final int CARD_GAP = 4;
    private static final int AVATAR = 24;
    private static final int MSG_AVATAR = 22;
    private static final int HEADER_H = 22;
    private static final int INPUT_H = 32;
    private static final int BTN = 18;
    private static final int STICKER_SIZE = 64;

    private static final int COL_BG = 0xFF0E1012;
    private static final int COL_BODY = 0xFF191C1F;
    private static final int COL_CARD = 0xFF3C3C3C;
    private static final int COL_CARD_HOVER = 0xFF4C4C4C;
    private static final int COL_CARD_SELECTED = 0xFF474747;
    private static final int COL_BORDER = 0xFF737373;
    private static final int COL_HEADER = 0xFF3C3C3C;
    private static final int COL_INPUT = 0xFF3C3C3C;
    private static final int COL_PILL = 0xFFEEECEC;
    private static final int COL_BUBBLE_OTHER = 0xFF4A4A4B;
    private static final int COL_BUBBLE_SELF = 0xFFEDEBEB;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_TEXT_DARK = 0xFF1A1A1A;
    private static final int COL_TEXT_DIM = 0xFF9A9A9A;
    private static final int COL_ACCENT = 0xFF5BD6F5;

    private record Card(String key, String name, UUID uuid) {
    }

    private static final class Bubble {
        final List<List<EmojiText.Piece>> lines;
        final int w;
        final int h;
        final String sticker;
        int y;

        Bubble(List<List<EmojiText.Piece>> lines, int w, int h) {
            this(lines, w, h, null);
        }

        Bubble(List<List<EmojiText.Piece>> lines, int w, int h, String sticker) {
            this.lines = lines;
            this.w = w;
            this.h = h;
            this.sticker = sticker;
        }
    }

    private static final class Run {
        final UUID sender;
        final String name;
        final List<Bubble> bubbles = new ArrayList<>();
        int y;

        Run(UUID sender, String name) {
            this.sender = sender;
            this.name = name;
        }
    }

    private static final class SysRow {
        final List<List<EmojiText.Piece>> lines;
        final boolean divider;
        int y;

        SysRow(List<List<EmojiText.Piece>> lines, boolean divider) {
            this.lines = lines;
            this.divider = divider;
        }
    }

    private final List<Card> cards = new ArrayList<>();
    private final List<Run> runs = new ArrayList<>();
    private final List<SysRow> systemRows = new ArrayList<>();
    /** 跨面板开关保留，退出世界时清空。 */
    private static final List<String> SENT_HISTORY = new ArrayList<>();

    private String selectedKey = "all";
    private int contentHeight;
    private int lastRevision = -1;
    private long lastCardRefresh;
    private int historyIndex = -1;

    private double scroll;
    private boolean stickToBottom = true;
    private double listScroll;

    private int margin;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyY;
    private int bodyH;

    private int pillX;
    private int pillY;
    private int pillW;
    private int pillH;
    private int btnY;
    private int sendX;
    private int stickerX;
    private int moreX;

    private int pickerX;
    private int pickerY;
    private int pickerW;
    private int pickerH;
    private static final int PICKER_W = 200;
    private static final int PICKER_H = 118;
    private static final int TAB_H = 14;

    private int menuX;
    private int menuY;
    private int menuW;
    private int menuH;
    private static final int MENU_ROW = 18;

    private TextFieldWidget input;
    private final List<String> completions = new ArrayList<>();
    private int completionStart;
    private int completionEnd;
    private int completionIndex = -1;
    private int completionToken;
    /** 补全列表对应的原始输入；按 Tab 循环时始终以它为底，避免越补越长。 */
    private String completionBaseText = "";
    private boolean applyingCompletion;
    private boolean emojiOpen;
    private boolean moreOpen;
    /** 0=表情 1=贴纸 2=颜文字 */
    private int pickerTab;
    private double pickerScroll;
    private boolean historyOpen;

    public BakerScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int m = MathHelper.clamp(Math.round(width * 0.014f), 8, 24);
        int gap = MathHelper.clamp(Math.round(width * 0.010f), 6, 14);

        margin = m;
        listX = m;
        listY = m + 20;
        listW = MathHelper.clamp(Math.round(width * 0.22f), 110, 210);
        listH = height - listY - m;

        panelX = listX + listW + gap;
        panelY = listY;
        panelW = width - panelX - m;
        panelH = listH;
        bodyY = panelY + HEADER_H;
        bodyH = panelH - HEADER_H - INPUT_H;

        int inputTop = panelY + panelH - INPUT_H;
        int right = panelX + panelW - 10;
        moreX = right - BTN;
        stickerX = moreX - 6 - BTN;
        sendX = stickerX - 6 - BTN;
        pillX = panelX + 10;
        pillW = Math.max(50, sendX - 6 - pillX);
        pillH = 20;
        pillY = inputTop + (INPUT_H - pillH) / 2;
        btnY = inputTop + (INPUT_H - BTN) / 2;

        pickerW = PICKER_W;
        pickerH = PICKER_H;
        pickerX = panelX + panelW - pickerW - 10;
        pickerY = inputTop - pickerH - 6;

        menuW = 118;
        menuH = MENU_ROW * 3 + 6;
        menuX = moreX + BTN - menuW;
        menuY = btnY - menuH - 4;

        input = new TextFieldWidget(textRenderer, pillX + 11, pillY + 6, Math.max(20, pillW - 22), 10, Text.literal(""));
        input.setDrawsBackground(false);
        input.setMaxLength(256);
        // 文字由我们自己画（要套面板字体），widget 只负责输入和光标状态
        input.setEditableColor(0x00000000);
        addDrawableChild(input);
        setInitialFocus(input);

        input.setChangedListener(text -> {
            historyIndex = -1;
            if (!applyingCompletion) {
                refreshCompletions();
            }
        });

        rebuildCards();
        layoutMessages();

        if (Boolean.getBoolean("bakerchat.debug.emoji")) {
            emojiOpen = true;
        }
        if (Boolean.getBoolean("bakerchat.debug.menu")) {
            moreOpen = true;
        }
        if (Boolean.getBoolean("bakerchat.debug.stickers")) {
            emojiOpen = true;
            pickerTab = 1;
        }
        if (Boolean.getBoolean("bakerchat.debug.kaomoji")) {
            emojiOpen = true;
            pickerTab = 2;
        }
        if (Boolean.getBoolean("bakerchat.debug.history")) {
            SENT_HISTORY.add("/time set day");
            SENT_HISTORY.add("/gamemode creative");
            SENT_HISTORY.add("/tp @s 0 64 0");
            historyOpen = true;
        }
        if (Boolean.getBoolean("bakerchat.debug.stickerinsert")) {
            ChatStore.addSticker(Stickers.NAMES[0]);
        }
    }

    private void rebuildCards() {
        cards.clear();
        cards.add(new Card("all", "全部消息", null));

        if (client != null && client.getNetworkHandler() != null) {
            List<PlayerListEntry> players = new ArrayList<>(client.getNetworkHandler().getPlayerList());
            players.sort(Comparator.comparing(entry -> entry.getProfile().getName(), String.CASE_INSENSITIVE_ORDER));
            for (PlayerListEntry player : players) {
                UUID id = player.getProfile().getId();
                cards.add(new Card("p:" + id, player.getProfile().getName(), id));
            }
        }

        cards.add(new Card("sys", "系统", null));
        lastCardRefresh = System.currentTimeMillis();
    }

    private Card selectedCard() {
        for (Card card : cards) {
            if (card.key().equals(selectedKey)) {
                return card;
            }
        }
        return cards.isEmpty() ? new Card("all", "全部消息", null) : cards.get(0);
    }

    private boolean matches(ChatStore.Entry entry) {
        if (entry.kind == ChatStore.Kind.DIVIDER) {
            return "all".equals(selectedKey);
        }
        if (entry.kind == ChatStore.Kind.STICKER) {
            return "all".equals(selectedKey);
        }
        if ("sys".equals(selectedKey)) {
            return entry.kind == ChatStore.Kind.SYSTEM;
        }
        if (selectedKey.startsWith("p:")) {
            return entry.kind == ChatStore.Kind.CHAT && selectedKey.substring(2).equals(String.valueOf(entry.sender));
        }
        return true;
    }

    private void layoutMessages() {
        runs.clear();
        systemRows.clear();

        int textMax = Math.max(50, Math.min((int) (panelW * 0.62f), 300) - 14);
        int y = 4;

        Run run = null;
        for (ChatStore.Entry entry : ChatStore.snapshot()) {
            if (!matches(entry)) {
                continue;
            }

            if (entry.kind == ChatStore.Kind.DIVIDER) {
                run = null;
                SysRow row = new SysRow(List.of(), true);
                row.y = y;
                systemRows.add(row);
                y += 12;
                continue;
            }

            if (entry.kind == ChatStore.Kind.STICKER) {
                run = null;
                UUID self = client != null && client.player != null ? client.player.getUuid() : null;
                Run stickerRun = new Run(self, "");
                stickerRun.y = y;
                Bubble bubble = new Bubble(List.of(), STICKER_SIZE, STICKER_SIZE, entry.sticker);
                bubble.y = y;
                stickerRun.bubbles.add(bubble);
                runs.add(stickerRun);
                y += STICKER_SIZE + 6;
                continue;
            }

            if (entry.kind == ChatStore.Kind.SYSTEM) {
                run = null;
                List<List<EmojiText.Piece>> lines = EmojiText.layout(textRenderer, entry.plain,
                        Math.max(70, panelW - 70));
                SysRow row = new SysRow(lines, false);
                row.y = y;
                systemRows.add(row);
                y += lines.size() * EmojiText.LINE_HEIGHT + 6;
                continue;
            }

            if (run == null || !run.sender.equals(entry.sender)) {
                run = new Run(entry.sender, entry.senderName);
                run.y = y;
                runs.add(run);
                y += 5;
            }

            List<List<EmojiText.Piece>> lines = EmojiText.layout(textRenderer, entry.plain, textMax);
            int lineW = 0;
            for (List<EmojiText.Piece> line : lines) {
                lineW = Math.max(lineW, EmojiText.lineWidth(line));
            }
            int w = Math.min(textMax, lineW) + 14;
            int h = lines.size() * EmojiText.LINE_HEIGHT + 8;

            Bubble bubble = new Bubble(lines, w, h);
            bubble.y = y;
            run.bubbles.add(bubble);
            y += h + 3;
        }

        contentHeight = Math.max(0, y + 4);
        lastRevision = ChatStore.revision();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bodyH - 8));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 补全弹窗有时会把裁剪框留在栈里，先清掉，否则这一帧的背景画不出来
        resetScissor(context);

        if (ChatStore.revision() != lastRevision) {
            layoutMessages();
        }
        if (System.currentTimeMillis() - lastCardRefresh > 2000L) {
            rebuildCards();
        }

        renderBackground(context, mouseX, mouseY, delta);
        drawTitle(context);
        drawList(context, mouseX, mouseY);
        drawHeader(context);
        drawMessages(context);
        drawInput(context, mouseX, mouseY);
        drawEmojiPicker(context, mouseX, mouseY);
        drawMoreMenu(context, mouseX, mouseY);
        drawHistoryPopup(context, mouseX, mouseY);

        if (input != null) {
            // 只画我们自己的那层：widget 仍然负责输入和光标状态，但不再让它渲染文字
            drawInputText(context);
        }
        drawCompletions(context);
    }

    /** 栈空时 disable 会抛异常，这里当清理用。 */
    private static void resetScissor(DrawContext context) {
        try {
            context.disableScissor();
        } catch (IllegalStateException ignored) {
            // 本来就没有裁剪框
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, COL_BG);
    }

    private void drawTitle(DrawContext context) {
        BakerDraw.text(context, textRenderer, TITLE.getString(), margin, margin - 2, COL_TEXT);
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int viewBottom = listY + listH - 26;
        int contentH = cards.size() * (CARD_H + CARD_GAP);

        listScroll = MathHelper.clamp(listScroll, 0, Math.max(0, contentH - (viewBottom - listY)));

        context.enableScissor(listX, listY, listX + listW, viewBottom);
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int y = (int) (listY + i * (CARD_H + CARD_GAP) - listScroll);
            if (y + CARD_H < listY || y > viewBottom) {
                continue;
            }

            boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + CARD_H;
            drawCard(context, card, listX, y, hovered);
        }
        context.disableScissor();

        drawReloadPill(context, mouseX, mouseY);
    }

    private void drawCard(DrawContext context, Card card, int x, int y, boolean hovered) {
        boolean selected = card.key().equals(selectedKey);

        BakerDraw.roundRectBorder(context, x, y, listW, CARD_H, 5, COL_BORDER,
                selected ? COL_CARD_SELECTED : hovered ? COL_CARD_HOVER : COL_CARD);

        if (selected) {
            BakerDraw.roundRect(context, x + 1, y + 1, 2, CARD_H - 2, 1, COL_ACCENT);
        }

        drawAvatar(context, card.uuid(), card.name(), x + 5, y + 5, AVATAR);

        int nameY = y + (CARD_H - 8) / 2;
        BakerDraw.text(context, textRenderer, card.name(), x + 5 + AVATAR + 8, nameY, COL_TEXT);
    }

    /** 头像：深色底 + 皮肤脸（或名字首字）。 */
    private void drawAvatar(DrawContext context, UUID uuid, String name, int x, int y, int size) {
        BakerDraw.roundRect(context, x, y, size, size, 3, 0xFF2A2A2A);

        Identifier skin = skinOf(uuid);
        if (skin != null) {
            BakerDraw.face(context, skin, x + 1, y + 1, size - 2);
        } else {
            String initial = name == null || name.isEmpty() ? "?" : name.substring(0, 1);
            int textW = BakerDraw.textWidth(textRenderer, initial);
            BakerDraw.text(context, textRenderer, initial,
                    x + (size - textW) / 2, y + (size - 8) / 2, 0xFFE8E8E8);
        }
    }

    private Identifier skinOf(UUID uuid) {
        if (uuid == null || client == null) {
            return null;
        }
        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return client.player.getSkinTextures().texture();
        }
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                return entry.getSkinTextures().texture();
            }
        }
        // 玩家已经离开列表时，至少给一张按 UUID 决定的默认皮肤
        return DefaultSkinHelper.getSkinTextures(uuid).texture();
    }

    private void drawReloadPill(DrawContext context, int mouseX, int mouseY) {
        int y = listY + listH - 20;
        boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + 20;

        BakerDraw.roundRect(context, listX, y, listW, 20, 10, hovered ? 0xFFFFFFFF : COL_PILL);
        BakerDraw.text(context, textRenderer, "回到最新", listX + 12, y + 7, COL_TEXT_DARK);
    }

    private void drawHeader(DrawContext context) {
        BakerDraw.roundRectBorder(context, panelX, panelY, panelW, HEADER_H, 7, COL_BORDER, COL_HEADER, true, false);

        // 左边 46x1 的渐变条竖向拉伸，右边 872x126 的科技纹按高度等比
        // 左上角是圆角，条纹往内缩一点，不然方角会从弧线里戳出来
        BakerDraw.texScaled(context, BakerTextures.HEADER_LEFT, panelX + 2, panelY + 3,
                9, HEADER_H - 5, 0.0f, 0.0f, 46, 1, 46, 1);

        int stripH = HEADER_H - 4;
        int stripW = Math.min((int) (stripH * (872.0f / 126.0f)), Math.max(0, panelW / 2));
        if (stripW > 24) {
            BakerDraw.texScaled(context, BakerTextures.HEADER_RIGHT, panelX + panelW - stripW - 2, panelY + 2,
                    stripW, stripH, 0.0f, 0.0f, 180, 26, 180, 26);
        }

        String name = selectedCard().name();
        int nameW = BakerDraw.textWidth(textRenderer, name);
        BakerDraw.text(context, textRenderer, name,
                panelX + (panelW - nameW) / 2 + 6, panelY + (HEADER_H - 8) / 2, COL_TEXT);

        BakerDraw.tex(context, BakerTextures.COLORS, panelX + panelW - 164, bodyY + 1, 160, 2, 160, 2);
    }

    private void drawMessages(DrawContext context) {
        context.fill(panelX, bodyY, panelX + panelW, bodyY + bodyH, COL_BODY);
        context.fill(panelX, bodyY, panelX + panelW - 120, bodyY + 1, 0x88FFFFFF);
        BakerDraw.tex(context, BakerTextures.TOP_DECO_RIGHT, panelX + panelW - 120, bodyY - 1, 120, 4, 120, 4);

        context.fill(panelX, bodyY + bodyH - 1, panelX + panelW, bodyY + bodyH, 0x44FFFFFF);
        context.fill(panelX, bodyY, panelX + 1, bodyY + bodyH, 0x44FFFFFF);
        context.fill(panelX + panelW - 1, bodyY, panelX + panelW, bodyY + bodyH, 0x44FFFFFF);

        int scrollMax = maxScroll();
        if (stickToBottom) {
            scroll = scrollMax;
        }
        scroll = MathHelper.clamp(scroll, 0, scrollMax);

        double offset = bodyY + 4 - scroll;

        context.enableScissor(panelX + 1, bodyY + 1, panelX + panelW - 1, bodyY + bodyH - 1);

        if (runs.isEmpty() && systemRows.isEmpty()) {
            int w = BakerDraw.textWidth(textRenderer, EMPTY);
            BakerDraw.text(context, textRenderer, EMPTY, panelX + panelW / 2 - w / 2, bodyY + bodyH / 2, COL_TEXT_DIM);
        }

        for (Run run : runs) {
            drawRun(context, run, (int) offset);
        }
        for (SysRow row : systemRows) {
            int y = (int) (offset + row.y);
            if (row.divider) {
                int cy = y + 5;
                context.fill(panelX + 24, cy, panelX + panelW - 24, cy + 1, 0x55A9A8A6);
                continue;
            }

            for (List<EmojiText.Piece> line : row.lines) {
                int lineX = panelX + (panelW - EmojiText.lineWidth(line)) / 2;
                EmojiText.draw(context, textRenderer, List.of(line), lineX, y, COL_TEXT_DIM);
                y += EmojiText.LINE_HEIGHT;
            }
        }

        context.disableScissor();

        if (scrollMax > 0) {
            int trackH = bodyH - 10;
            int thumbH = Math.max(14, (int) (trackH * (bodyH - 8.0) / contentHeight));
            int thumbY = bodyY + 5 + (int) ((trackH - thumbH) * (scroll / scrollMax));
            context.fill(panelX + panelW - 3, thumbY, panelX + panelW - 1, thumbY + thumbH, 0x66FFFFFF);
        }
    }

    private void drawRun(DrawContext context, Run run, int offset) {
        boolean self = run.sender != null && client != null && client.player != null
                && run.sender.equals(client.player.getUuid());
        int avatarX = self ? panelX + panelW - 6 - MSG_AVATAR : panelX + 6;

        // 贴纸不带头像，Baker 里贴纸就是单独一张图
        boolean stickerOnly = !run.bubbles.isEmpty() && run.bubbles.get(0).sticker != null;
        if (!stickerOnly) {
            drawAvatar(context, run.sender, run.name, avatarX, offset + run.y, MSG_AVATAR);
        }

        int contentLeft = panelX + 6 + MSG_AVATAR + 6;
        int contentRight = panelX + panelW - 6 - MSG_AVATAR - 6;
        if (stickerOnly) {
            contentLeft = panelX + 10;
            contentRight = panelX + panelW - 10;
        }

        for (Bubble bubble : run.bubbles) {
            int x = self ? contentRight - bubble.w : contentLeft;
            int y = offset + bubble.y;

            if (bubble.sticker != null) {
                BakerDraw.texScaled(context, Stickers.texture(bubble.sticker), x, y, bubble.w, bubble.h,
                        0.0f, 0.0f, 64, 64, 64, 64);
                continue;
            }

            int fill = self ? COL_BUBBLE_SELF : COL_BUBBLE_OTHER;
            int color = self ? COL_TEXT_DARK : COL_TEXT;

            BakerDraw.roundRect(context, x, y, bubble.w, bubble.h, 6, fill);
            EmojiText.draw(context, textRenderer, bubble.lines, x + 7, y + 4, color);
        }
    }

    private void drawInput(DrawContext context, int mouseX, int mouseY) {
        int top = panelY + panelH - INPUT_H;

        BakerDraw.roundRectBorder(context, panelX, top, panelW, INPUT_H, 7, COL_BORDER, COL_INPUT, false, true);
        BakerDraw.roundRect(context, pillX, pillY, pillW, pillH, pillH / 2, COL_PILL);

        drawHistoryButton(context, sendX, btnY, mouseX, mouseY);
        drawEmojiButton(context, stickerX, btnY, mouseX, mouseY);
        drawCircleButton(context, moreX, btnY, BakerTextures.ICON_MORE, 12, 12, mouseX, mouseY);
    }

    /** 表情按钮直接用笑脸表情当图标，比缩小原图标清楚。 */
    private void drawEmojiButton(DrawContext context, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + BTN && mouseY >= y && mouseY <= y + BTN;
        BakerDraw.roundRect(context, x, y, BTN, BTN, BTN / 2, hovered ? 0xFFFFFFFF : COL_PILL);
        context.drawTexture(BakerEmoji.texture(0), x + 3, y + 3, 12, 12, 0.0f, 0.0f, 24, 24, 24, 24);
    }

    /** 输入框的文字和光标自己画，才能套上面板字体。 */
    private void drawInputText(DrawContext context) {
        if (input == null) {
            return;
        }

        int x = pillX + 11;
        int y = pillY + 7;

        String text = input.getText();
        if (text.isEmpty()) {
            BakerDraw.text(context, textRenderer, PLACEHOLDER, x, y, 0xFF9A9A9A);
            return;
        }

        int maxW = Math.max(10, pillW - 24);
        int cursor = MathHelper.clamp(input.getCursor(), 0, text.length());

        int start = 0;
        while (start < cursor && BakerDraw.textWidth(textRenderer, text.substring(start, cursor)) > maxW) {
            start++;
        }

        String visible = text.substring(start);
        while (!visible.isEmpty() && BakerDraw.textWidth(textRenderer, visible) > maxW) {
            visible = visible.substring(0, visible.length() - 1);
        }

        BakerDraw.text(context, textRenderer, visible, x, y, COL_TEXT_DARK);

        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = x + BakerDraw.textWidth(textRenderer, text.substring(start, cursor));
            context.fill(caretX, pillY + 5, caretX + 1, pillY + pillH - 3, COL_TEXT_DARK);
        }
    }

    /** 最近命令按钮：三条横线。 */
    private void drawHistoryButton(DrawContext context, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + BTN && mouseY >= y && mouseY <= y + BTN;
        BakerDraw.roundRect(context, x, y, BTN, BTN, BTN / 2, hovered ? 0xFFFFFFFF : COL_PILL);

        int bx = x + 5;
        int by = y + 5;
        context.fill(bx, by, bx + 8, by + 1, 0xFF3C3C3C);
        context.fill(bx, by + 3, bx + 6, by + 4, 0xFF3C3C3C);
        context.fill(bx, by + 6, bx + 8, by + 7, 0xFF3C3C3C);
    }

    private void drawCircleButton(DrawContext context, int x, int y, Identifier icon, int iconW, int iconH,
                                  int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + BTN && mouseY >= y && mouseY <= y + BTN;
        BakerDraw.roundRect(context, x, y, BTN, BTN, BTN / 2, hovered ? 0xFFFFFFFF : COL_PILL);
        BakerDraw.tex(context, icon, x + (BTN - iconW) / 2, y + (BTN - iconH) / 2, iconW, iconH, iconW, iconH);
    }

    private void drawEmojiPicker(DrawContext context, int mouseX, int mouseY) {
        if (!emojiOpen) {
            return;
        }

        BakerDraw.roundRectBorder(context, pickerX, pickerY, pickerW, pickerH, 6, COL_BORDER, 0xF21C1C1C);

        // 顶部三个标签
        String[] tabs = {"表情", "贴纸", "颜文字"};
        int tabW = (pickerW - 8) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = pickerX + 4 + i * tabW;
            boolean active = i == pickerTab;
            boolean hovered = mouseX >= tx && mouseX <= tx + tabW && mouseY >= pickerY + 3 && mouseY <= pickerY + 3 + TAB_H;

            if (active || hovered) {
                BakerDraw.roundRect(context, tx, pickerY + 3, tabW - 2, TAB_H, 3, active ? 0xFF3C3C3C : 0xFF2A2A2A);
            }

            int tw = BakerDraw.textWidth(textRenderer, tabs[i]);
            BakerDraw.text(context, textRenderer, tabs[i], tx + (tabW - 2 - tw) / 2, pickerY + 6,
                    active ? COL_TEXT : COL_TEXT_DIM);
        }

        int contentY = pickerY + TAB_H + 6;
        int contentH = pickerH - TAB_H - 8;

        switch (pickerTab) {
            case 0 -> drawEmojiGrid(context, mouseX, mouseY, contentY, contentH);
            case 1 -> drawStickerGrid(context, mouseX, mouseY, contentY, contentH);
            default -> drawKaomojiList(context, mouseX, mouseY, contentY, contentH);
        }
    }

    private static final int EMOJI_CELL = 16;
    private static final int EMOJI_COLS = 10;
    private static final int STICKER_CELL = 44;
    private static final int STICKER_COLS = 4;
    private static final int KAOMOJI_ROW = 13;

    private void drawEmojiGrid(DrawContext context, int mouseX, int mouseY, int contentY, int contentH) {
        int gridW = EMOJI_COLS * EMOJI_CELL;
        int x0 = pickerX + (pickerW - gridW) / 2;

        for (int i = 0; i < BakerEmoji.size(); i++) {
            int x = x0 + (i % EMOJI_COLS) * EMOJI_CELL;
            int y = contentY + (i / EMOJI_COLS) * EMOJI_CELL;

            if (mouseX >= x && mouseX < x + EMOJI_CELL && mouseY >= y && mouseY < y + EMOJI_CELL) {
                BakerDraw.roundRect(context, x, y, EMOJI_CELL, EMOJI_CELL, 3, 0xFF3C3C3C);
            }

            context.drawTexture(BakerEmoji.texture(i), x + 3, y + 3, 10, 10, 0.0f, 0.0f, 24, 24, 24, 24);
        }
    }

    private void drawStickerGrid(DrawContext context, int mouseX, int mouseY, int contentY, int contentH) {
        int gridW = STICKER_COLS * STICKER_CELL;
        int x0 = pickerX + (pickerW - gridW) / 2;
        int totalRows = (Stickers.NAMES.length + STICKER_COLS - 1) / STICKER_COLS;
        int visibleRows = Math.max(1, contentH / STICKER_CELL);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        int startRow = MathHelper.clamp((int) pickerScroll, 0, maxScroll);

        context.enableScissor(pickerX + 2, contentY, pickerX + pickerW - 2, contentY + contentH);

        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < STICKER_COLS; col++) {
                int index = (startRow + row) * STICKER_COLS + col;
                if (index >= Stickers.NAMES.length) {
                    continue;
                }

                int x = x0 + col * STICKER_CELL;
                int y = contentY + row * STICKER_CELL;

                if (mouseX >= x && mouseX < x + STICKER_CELL && mouseY >= y && mouseY < y + STICKER_CELL) {
                    BakerDraw.roundRect(context, x, y, STICKER_CELL, STICKER_CELL, 3, 0xFF3C3C3C);
                }

                BakerDraw.texScaled(context, Stickers.texture(Stickers.NAMES[index]), x + 6, y + 6, 32, 32,
                        0.0f, 0.0f, 64, 64, 64, 64);
            }
        }

        context.disableScissor();

        if (maxScroll > 0) {
            int trackH = contentH - 4;
            int thumbH = Math.max(12, trackH * visibleRows / totalRows);
            int thumbY = contentY + 2 + (int) ((trackH - thumbH) * (startRow / (float) maxScroll));
            context.fill(pickerX + pickerW - 4, thumbY, pickerX + pickerW - 2, thumbY + thumbH, 0x66FFFFFF);
        }
    }

    private void drawKaomojiList(DrawContext context, int mouseX, int mouseY, int contentY, int contentH) {
        int visibleRows = Math.max(1, contentH / KAOMOJI_ROW);
        int maxScroll = Math.max(0, Kaomoji.LIST.length - visibleRows);
        int start = MathHelper.clamp((int) pickerScroll, 0, maxScroll);

        context.enableScissor(pickerX + 2, contentY, pickerX + pickerW - 2, contentY + contentH);

        for (int row = 0; row < visibleRows; row++) {
            int index = start + row;
            if (index >= Kaomoji.LIST.length) {
                break;
            }

            int y = contentY + row * KAOMOJI_ROW;
            boolean hovered = mouseX >= pickerX + 3 && mouseX <= pickerX + pickerW - 3 && mouseY >= y && mouseY < y + KAOMOJI_ROW;
            if (hovered) {
                context.fill(pickerX + 3, y, pickerX + pickerW - 3, y + KAOMOJI_ROW, 0x33FFFFFF);
            }

            BakerDraw.text(context, textRenderer, Kaomoji.LIST[index], pickerX + 8, y + 3, COL_TEXT);
        }

        context.disableScissor();

        if (maxScroll > 0) {
            int trackH = contentH - 4;
            int thumbH = Math.max(12, trackH * visibleRows / Kaomoji.LIST.length);
            int thumbY = contentY + 2 + (int) ((trackH - thumbH) * (start / (float) maxScroll));
            context.fill(pickerX + pickerW - 4, thumbY, pickerX + pickerW - 2, thumbY + thumbH, 0x66FFFFFF);
        }
    }

    /** 面板内容区的点击：三种标签各自的命中判定。 */
    private void onPickerClick(double mouseX, double mouseY) {
        int contentY = pickerY + TAB_H + 6;
        int contentH = pickerH - TAB_H - 8;

        switch (pickerTab) {
            case 0 -> {
                int gridW = EMOJI_COLS * EMOJI_CELL;
                int x0 = pickerX + (pickerW - gridW) / 2;
                int col = (int) ((mouseX - x0) / EMOJI_CELL);
                int row = (int) ((mouseY - contentY) / EMOJI_CELL);
                int index = row * EMOJI_COLS + col;
                if (col >= 0 && col < EMOJI_COLS && row >= 0 && index < BakerEmoji.size()) {
                    insert(":" + BakerEmoji.NAMES[index] + ":");
                }
            }
            case 1 -> {
                int gridW = STICKER_COLS * STICKER_CELL;
                int x0 = pickerX + (pickerW - gridW) / 2;
                int visibleRows = Math.max(1, contentH / STICKER_CELL);
                int totalRows = (Stickers.NAMES.length + STICKER_COLS - 1) / STICKER_COLS;
                int startRow = MathHelper.clamp((int) pickerScroll, 0, Math.max(0, totalRows - visibleRows));

                int col = (int) ((mouseX - x0) / STICKER_CELL);
                int row = (int) ((mouseY - contentY) / STICKER_CELL);
                int index = (startRow + row) * STICKER_COLS + col;

                if (col >= 0 && col < STICKER_COLS && row >= 0 && row < visibleRows
                        && index >= 0 && index < Stickers.NAMES.length) {
                    ChatStore.addSticker(Stickers.NAMES[index]);
                    stickToBottom = true;
                    emojiOpen = false;
                }
            }
            default -> {
                int visibleRows = Math.max(1, contentH / KAOMOJI_ROW);
                int start = MathHelper.clamp((int) pickerScroll, 0, Math.max(0, Kaomoji.LIST.length - visibleRows));
                int row = (int) ((mouseY - contentY) / KAOMOJI_ROW);
                int index = start + row;
                if (row >= 0 && row < visibleRows && index < Kaomoji.LIST.length) {
                    insert(Kaomoji.LIST[index]);
                }
            }
        }
    }

    /** 最近发送过的指令，点一条放进输入框，由用户自己决定发不发。 */
    private void drawHistoryPopup(DrawContext context, int mouseX, int mouseY) {
        if (!historyOpen) {
            return;
        }

        List<String> commands = recentCommands();
        int rowH = 12;
        int rows = Math.min(commands.size(), COMPLETION_MAX);
        int width = 120;
        for (String command : commands) {
            width = Math.max(width, BakerDraw.textWidth(textRenderer, command) + 14);
        }
        width = Math.min(width, Math.max(60, panelW - 20));

        int height = Math.max(1, rows) * rowH + 6;
        int x = pillX;
        int y = Math.max(panelY + HEADER_H + 2, pillY - height - 4);

        BakerDraw.roundRectBorder(context, x, y, width, height, 4, COL_BORDER, 0xF2141618);

        if (commands.isEmpty()) {
            BakerDraw.text(context, textRenderer, "还没发过指令", x + 7, y + 5, COL_TEXT_DIM);
            return;
        }

        for (int i = 0; i < rows; i++) {
            int rowY = y + 3 + i * rowH;
            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= rowY && mouseY < rowY + rowH;
            if (hovered) {
                context.fill(x + 2, rowY, x + width - 2, rowY + rowH, 0x33FFFFFF);
            }
            BakerDraw.text(context, textRenderer, commands.get(i), x + 7, rowY + 2, COL_TEXT);
        }
    }

    private List<String> recentCommands() {
        List<String> commands = new ArrayList<>();
        for (int i = SENT_HISTORY.size() - 1; i >= 0 && commands.size() < COMPLETION_MAX; i--) {
            String entry = SENT_HISTORY.get(i);
            if (entry.startsWith("/")) {
                commands.add(entry);
            }
        }
        return commands;
    }

    private int historyHit(double mouseX, double mouseY) {
        if (!historyOpen) {
            return -1;
        }

        List<String> commands = recentCommands();
        if (commands.isEmpty()) {
            return -1;
        }

        int rowH = 12;
        int rows = Math.min(commands.size(), COMPLETION_MAX);
        int width = 120;
        for (String command : commands) {
            width = Math.max(width, BakerDraw.textWidth(textRenderer, command) + 14);
        }
        width = Math.min(width, Math.max(60, panelW - 20));

        int height = rows * rowH + 6;
        int x = pillX;
        int y = Math.max(panelY + HEADER_H + 2, pillY - height - 4);
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return -1;
        }

        int row = (int) ((mouseY - y - 3) / rowH);
        return row >= 0 && row < rows ? row : -1;
    }

    private void drawMoreMenu(DrawContext context, int mouseX, int mouseY) {
        if (!moreOpen) {
            return;
        }

        BakerDraw.roundRectBorder(context, menuX, menuY, menuW, menuH, 5, COL_BORDER, 0xF21C1C1C);

        String[] labels = {"插入分隔线", "插入状态文字", "清空本地记录"};
        for (int i = 0; i < labels.length; i++) {
            int y = menuY + 3 + i * MENU_ROW;
            boolean hovered = mouseX >= menuX && mouseX <= menuX + menuW && mouseY >= y && mouseY < y + MENU_ROW;
            if (hovered) {
                context.fill(menuX + 2, y, menuX + menuW - 2, y + MENU_ROW, 0x33FFFFFF);
            }
            BakerDraw.text(context, textRenderer, labels[i], menuX + 8, y + 5, COL_TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (inGame() && !completions.isEmpty() && completionHit(mouseX, mouseY) >= 0) {
            applyCompletion(completionHit(mouseX, mouseY));
            completions.clear();
            completionIndex = -1;
            return true;
        }

        if (historyOpen) {
            int hit = historyHit(mouseX, mouseY);
            if (hit >= 0) {
                List<String> commands = recentCommands();
                if (hit < commands.size()) {
                    input.setText(commands.get(hit));
                    input.setCursorToEnd(false);
                    historyOpen = false;
                    return true;
                }
            }
            historyOpen = false;
        }

        if (emojiOpen && inRect(mouseX, mouseY, pickerX, pickerY, pickerW, pickerH)) {
            // 标签行
            if (mouseY <= pickerY + 3 + TAB_H) {
                int tabW = (pickerW - 8) / 3;
                int tab = (int) ((mouseX - pickerX - 4) / tabW);
                if (tab >= 0 && tab < 3) {
                    pickerTab = tab;
                    pickerScroll = 0;
                }
                return true;
            }

            onPickerClick(mouseX, mouseY);
            return true;
        }

        if (moreOpen) {
            if (inRect(mouseX, mouseY, menuX, menuY, menuW, menuH)) {
                onMoreAction((int) ((mouseY - menuY - 3) / MENU_ROW));
                return true;
            }
            moreOpen = false;
        }

        for (int i = 0; i < cards.size(); i++) {
            int y = (int) (listY + i * (CARD_H + CARD_GAP) - listScroll);
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + CARD_H) {
                selectedKey = cards.get(i).key();
                scroll = 0;
                stickToBottom = true;
                layoutMessages();
                return true;
            }
        }

        int pillTop = listY + listH - 20;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= pillTop && mouseY <= pillTop + 20) {
            stickToBottom = true;
            scroll = maxScroll();
            return true;
        }

        if (inRect(mouseX, mouseY, sendX, btnY, BTN, BTN)) {
            historyOpen = !historyOpen;
            emojiOpen = false;
            moreOpen = false;
            return true;
        }
        if (inRect(mouseX, mouseY, stickerX, btnY, BTN, BTN)) {
            emojiOpen = !emojiOpen;
            moreOpen = false;
            return true;
        }
        if (inRect(mouseX, mouseY, moreX, btnY, BTN, BTN)) {
            moreOpen = !moreOpen;
            emojiOpen = false;
            return true;
        }

        emojiOpen = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onMoreAction(int row) {
        switch (row) {
            case 0 -> ChatStore.addDivider();
            case 1 -> {
                if (input != null && !input.getText().trim().isEmpty()) {
                    ChatStore.addSystem(Text.literal(input.getText().trim()));
                    input.setText("");
                }
            }
            case 2 -> ChatStore.clear();
            default -> {
            }
        }
        moreOpen = false;
        stickToBottom = true;
    }

    /** 往输入框里塞文字，保持焦点。 */
    private void insert(String text) {
        if (input == null) {
            return;
        }
        input.setText(input.getText() + text);
        input.setCursorToEnd(false);
        input.setFocused(true);
    }

    private static boolean inRect(double x, double y, int rx, int ry, int w, int h) {
        return x >= rx && x <= rx + w && y >= ry && y <= ry + h;
    }

    /** 标题界面下 client.player / networkHandler 都是 null，补全这类功能要跳过。 */
    private boolean inGame() {
        return client != null && client.player != null && client.getNetworkHandler() != null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double verticalAmount) {
        if (emojiOpen && pickerTab != 0 && inRect(mouseX, mouseY, pickerX, pickerY, pickerW, pickerH)) {
            pickerScroll = Math.max(0, pickerScroll - verticalAmount);
            return true;
        }

        if (mouseX >= listX && mouseX <= listX + listW) {
            listScroll -= verticalAmount * 20;
            return true;
        }

        if (mouseX >= panelX && mouseX <= panelX + panelW) {
            scroll -= verticalAmount * 20;
            stickToBottom = scroll >= maxScroll() - 1;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, amount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!completions.isEmpty()) {
            if (keyCode == 258) { // Tab
                applyCompletion(completionIndex + 1);
                return true;
            }
            if (keyCode == 265) { // ↑
                completionIndex = Math.floorMod(completionIndex - 1, completions.size());
                return true;
            }
            if (keyCode == 264) { // ↓
                completionIndex = Math.floorMod(completionIndex + 1, completions.size());
                return true;
            }
        }

        if (keyCode == 257 || keyCode == 335) {
            send();
            return true;
        }
        if (keyCode == 265 && !SENT_HISTORY.isEmpty()) {
            if (historyIndex < 0) {
                historyIndex = SENT_HISTORY.size() - 1;
            } else if (historyIndex > 0) {
                historyIndex--;
            }
            input.setText(SENT_HISTORY.get(historyIndex));
            input.setCursorToEnd(false);
            return true;
        }
        if (keyCode == 264 && !SENT_HISTORY.isEmpty()) {
            if (historyIndex >= 0) {
                historyIndex++;
                if (historyIndex >= SENT_HISTORY.size()) {
                    historyIndex = -1;
                    input.setText("");
                } else {
                    input.setText(SENT_HISTORY.get(historyIndex));
                    input.setCursorToEnd(false);
                }
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send() {
        if (input == null) {
            return;
        }

        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        if (SENT_HISTORY.isEmpty() || !SENT_HISTORY.get(SENT_HISTORY.size() - 1).equals(text)) {
            SENT_HISTORY.add(text);
        }
        historyIndex = -1;
        input.setText("");
        emojiOpen = false;
        completions.clear();
        completionIndex = -1;

        if (client == null || client.getNetworkHandler() == null) {
            return;
        }

        if (text.startsWith("/")) {
            client.getNetworkHandler().sendCommand(text.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(text);
        }

        stickToBottom = true;
        input.setFocused(true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 退出世界时清掉最近命令。 */
    public static void clearSentHistory() {
        SENT_HISTORY.clear();
    }

    /** 调试用：预填输入框并触发补全。 */
    void debugSetInput(String text) {
        if (input == null) {
            return;
        }
        input.setText(text);
        input.setCursorToEnd(false);
        refreshCompletions();
    }

    // ---- 指令补全 ----

    private static final int COMPLETION_ROW = 11;
    private static final int COMPLETION_MAX = 8;

    /** 用 Brigadier 算补全；结果可能异步返回，用 token 丢掉过期的那次。 */
    private void refreshCompletions() {
        int token = ++completionToken;
        completions.clear();
        completionIndex = -1;

        if (!inGame() || input == null) {
            return;
        }

        String text = input.getText();
        if (text.isEmpty() || text.charAt(0) != '/') {
            return;
        }

        completionBaseText = text;

        StringReader reader = new StringReader(text);
        reader.skip();

        try {
            CommandDispatcher<CommandSource> dispatcher = client.getNetworkHandler().getCommandDispatcher();
            ParseResults<CommandSource> parsed = dispatcher.parse(reader, client.getNetworkHandler().getCommandSource());
            dispatcher.getCompletionSuggestions(parsed).thenAccept(suggestions -> {
                if (token != completionToken) {
                    return;
                }

                List<String> found = new ArrayList<>();
                for (Suggestion suggestion : suggestions.getList()) {
                    found.add(suggestion.getText());
                }

                int start = suggestions.getRange().getStart();
                int end = suggestions.getRange().getEnd();

                client.execute(() -> {
                    completions.clear();
                    completions.addAll(found);
                    completionStart = start;
                    completionEnd = end;
                });
            });
        } catch (Exception ignored) {
            // 解析失败就当没有补全
        }
    }

    private void applyCompletion(int index) {
        if (completions.isEmpty() || input == null) {
            return;
        }

        int slot = Math.floorMod(index, completions.size());
        String value = completions.get(slot);
        String base = completionBaseText;
        int start = MathHelper.clamp(completionStart, 0, base.length());
        int end = MathHelper.clamp(completionEnd, start, base.length());
        String quoted = value.indexOf(' ') >= 0 ? "\"" + value + "\"" : value;

        // 每次都从原始输入重新拼，按 Tab 才能在同一批候选里来回切
        applyingCompletion = true;
        input.setText(base.substring(0, start) + quoted + base.substring(end));
        input.setCursorToEnd(false);
        applyingCompletion = false;
        completionIndex = slot;
    }

    private int completionHeight() {
        return Math.min(completions.size(), COMPLETION_MAX) * COMPLETION_ROW + 4;
    }

    private int completionWidth() {
        int width = 40;
        for (String completion : completions) {
            width = Math.max(width, BakerDraw.textWidth(textRenderer, completion));
        }
        return Math.min(width + 12, Math.max(40, panelW - 20));
    }

    private int completionY() {
        return Math.max(panelY + HEADER_H + 2, pillY - completionHeight() - 4);
    }

    private void drawCompletions(DrawContext context) {
        if (completions.isEmpty() || input == null) {
            return;
        }

        int x = pillX;
        int y = completionY();
        int w = completionWidth();
        int rows = Math.min(completions.size(), COMPLETION_MAX);

        BakerDraw.roundRectBorder(context, x, y, w, completionHeight(), 4, COL_BORDER, 0xF2141618);

        for (int i = 0; i < rows; i++) {
            int rowY = y + 2 + i * COMPLETION_ROW;
            if (i == Math.max(0, completionIndex)) {
                context.fill(x + 2, rowY, x + w - 2, rowY + COMPLETION_ROW, 0x33FFFFFF);
            }
            BakerDraw.text(context, textRenderer, completions.get(i), x + 6, rowY + 2, COL_TEXT);
        }
    }

    private int completionHit(double mouseX, double mouseY) {
        if (completions.isEmpty()) {
            return -1;
        }

        int x = pillX;
        int y = completionY();
        int w = completionWidth();
        if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + completionHeight()) {
            return -1;
        }

        int row = (int) ((mouseY - y - 2) / COMPLETION_ROW);
        return row >= 0 && row < Math.min(completions.size(), COMPLETION_MAX) ? row : -1;
    }

    /** 调试用：等价于按一次 Tab。 */
    void debugTab() {
        if (!completions.isEmpty()) {
            applyCompletion(completionIndex + 1);
        }
    }
}
