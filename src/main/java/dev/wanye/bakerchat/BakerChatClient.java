package dev.wanye.bakerchat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 客户端入口。只做一件事：把收到的聊天抄一份到 ChatStore。
 */
public class BakerChatClient implements ClientModInitializer {
    private static String currentServerKey = "";
    private static int saveTimer;

    @Override
    public void onInitializeClient() {
        ChatStore.init(FabricLoader.getInstance().getConfigDir());

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender != null) {
                ChatStore.addChat(sender.getId(), sender.getName(), message);
            } else {
                ChatStore.addSystem(message);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // overlay = actionbar，那是血条上面那行，不算聊天。
            if (!overlay) {
                ChatStore.addSystem(message);
            }
        });

        // 同一个世界/服务器：保留上次的记录；换了地方：重新开始。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String key = serverKey(client);
            if (!key.equals(currentServerKey)) {
                currentServerKey = key;
                BakerScreen.clearSentHistory();
            }
            ChatStore.enterWorld(key);
        });

        // 断开时只是把记录写盘，不清空；换服由 JOIN 那边处理。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChatStore.flush());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++saveTimer >= 100) {
                saveTimer = 0;
                ChatStore.flush();
            }
        });

        // 开发期截图用：
        //   -Dbakerchat.debug.open=true        标题画面直接开面板 + 假消息
        //   -Dbakerchat.debug.completion=true  进世界后开面板并输入 "/"，看补全弹窗
        //   -Dbakerchat.debug.reopen=true      进世界后：开面板 → 发消息 → 关 → 再开 → 截图
        boolean debugOpen = Boolean.getBoolean("bakerchat.debug.open");
        boolean debugCompletion = Boolean.getBoolean("bakerchat.debug.completion");

        if (Boolean.getBoolean("bakerchat.debug.reopen")) {
            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private int ticks;

                @Override
                public void onEndTick(net.minecraft.client.MinecraftClient client) {
                    if (client.player == null) {
                        return;
                    }

                    ticks++;
                    switch (ticks) {
                        case 20 -> {
                            if (client.currentScreen == null) {
                                client.setScreen(new BakerScreen());
                            }
                        }
                        case 28 -> client.getNetworkHandler().sendChatMessage("持久化测试");
                        case 34 -> {
                            if (client.currentScreen instanceof BakerScreen) {
                                client.setScreen(null);
                            }
                        }
                        case 44 -> {
                            if (client.currentScreen == null) {
                                client.setScreen(new BakerScreen());
                            }
                        }
                        case 60 -> net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                                client.runDirectory, client.getFramebuffer(), text -> {
                                });
                        default -> {
                        }
                    }
                }
            });
        }

        if (debugOpen || debugCompletion) {
            if (debugOpen) {
                seedDebugMessages();
            }

            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private boolean opened;
                private boolean shot;
                private int ticks;
                private BakerScreen panel;

                @Override
                public void onEndTick(net.minecraft.client.MinecraftClient client) {
                    if (!opened) {
                        if (debugOpen && client.currentScreen instanceof TitleScreen) {
                            opened = true;
                            panel = new BakerScreen();
                            client.setScreen(panel);
                        } else if (debugCompletion && client.player != null && client.currentScreen == null) {
                            opened = true;
                            panel = new BakerScreen();
                            client.setScreen(panel);
                            if (Boolean.getBoolean("bakerchat.debug.suggest")) {
                                panel.debugSetInput("/");
                            }
                        }
                        return;
                    }

                    if (client.getOverlay() == null && client.currentScreen instanceof BakerScreen) {
                        ticks++;
                        if (ticks == 30 && panel != null && Boolean.getBoolean("bakerchat.debug.suggest")) {
                            panel.debugTab();
                        }
                        if (ticks == 38 && panel != null && Boolean.getBoolean("bakerchat.debug.suggest")) {
                            panel.debugTab();
                        }
                        if (!shot && ticks == 50) {
                            shot = true;
                            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                                    client.runDirectory, client.getFramebuffer(), text -> {
                                    });
                        }
                    }
                }
            });
        }
    }

    /** 记录按“哪个服务器/哪个存档”分开存，换地方不会串消息。 */
    private static String serverKey(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            return "server:" + client.getCurrentServerEntry().address;
        }
        if (client.getServer() != null) {
            return "sp:" + client.getServer().getSaveProperties().getLevelName();
        }
        return "sp";
    }

    private void seedDebugMessages() {
        UUID perlica = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID chen = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID wulf = UUID.fromString("00000000-0000-0000-0000-000000000003");

        ChatStore.addSystem(Text.literal("Player275 joined the game"));
        ChatStore.addChat(perlica, "Perlica", Text.literal("<Perlica> 管理员？还没休息吗？ :stareye:"));
        ChatStore.addChat(perlica, "Perlica", Text.literal("<Perlica> 你没看到也没关系，这只是一点……不算重要的闲聊。"));
        ChatStore.addChat(chen, "ChenQianyu", Text.literal("<ChenQianyu> 你好像很喜欢在舰桥上看远处的塔卫二。其实我也一样。 :smile:"));
        ChatStore.addChat(chen, "ChenQianyu", Text.literal("<ChenQianyu> 这让我想起还在武陵的时候。我偶尔会利用那些风筝，站在高处，看着远方。"));
        ChatStore.addChat(perlica, "Perlica", Text.literal("<Perlica> 新的委托已经发到终端了 :thumb: :plusone: 记得看一眼。"));
        ChatStore.addDivider();
        ChatStore.addSystem(Text.literal("Wulfgard left the game"));
        ChatStore.addChat(wulf, "Wulfgard", Text.literal("<Wulfgard> 每次我想离它更近的时候，现实却总会将我推得更远。"));
        ChatStore.addChat(perlica, "Perlica", Text.literal("<Perlica> 站着在高空眺望，它离我是那么近，却又那么遥远。 :sad:"));
    }
}
