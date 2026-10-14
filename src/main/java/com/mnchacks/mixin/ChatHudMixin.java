package com.mnchacks.mixin;

import com.mnchacks.AntiSpamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti Spam: identical chat messages arriving within a second of the
 * previous one are suppressed, so flood-spam and repeated AFK messages stop
 * filling the chat.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

	@Unique
	private static String celarix$lastMessage = "";
	@Unique
	private static int celarix$lastTick = -1000;

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("HEAD"), cancellable = true)
	private void celarix$antiSpam(Text message, MessageSignatureData signature,
								  MessageIndicator indicator, CallbackInfo info) {
		if (!AntiSpamModule.isEnabled()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		int now = client.world != null
				? (int) client.world.getTime()
				: (int) (System.currentTimeMillis() / 50L);
		String text = message.getString();
		if (text.equals(celarix$lastMessage) && now - celarix$lastTick < 20) {
			info.cancel();
			return;
		}
		celarix$lastMessage = text;
		celarix$lastTick = now;
	}
}
