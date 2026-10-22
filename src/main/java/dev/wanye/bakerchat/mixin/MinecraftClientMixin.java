package dev.wanye.bakerchat.mixin;

import dev.wanye.bakerchat.BakerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版按 T / 斜杠 会 new 一个 ChatScreen。这里在它真正被设置之前换掉，
 * 这样 "打开聊天" 的所有入口（按键、别的 mod、指令）都会走到 Baker 面板。
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void bakerchat$replaceChatScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof ChatScreen) {
            MinecraftClient client = (MinecraftClient) (Object) this;
            client.setScreen(new BakerScreen());
            ci.cancel();
        }
    }
}
