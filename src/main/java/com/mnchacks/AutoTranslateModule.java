package com.mnchacks;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto Translate — every chat message you receive is queued to a background
 * thread, sent to a machine-translation service (auto-detecting the source
 * language) and the English result is printed into your chat as a local
 * "[TR]" line. The original message stays untouched; translations are only
 * shown when they actually differ from the original, so English servers stay
 * clean. Results are cached and requests are rate-limited.
 *
 * <p>Providers: Google's public gtx endpoint (auto-detect) with MyMemory as
 * a fallback. Note: chat text you receive is sent to that third-party service
 * while this hack is enabled.</p>
 */
public final class AutoTranslateModule {

	private static final String GTX_ENDPOINT =
			"https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=";
	private static final String MYMEMORY_ENDPOINT =
			"https://api.mymemory.translated.net/get?q=";
	private static final Pattern GTX_RESULT =
			Pattern.compile("\\[\\[\\[\"((?:\\\\.|[^\"\\\\])*)\",\"");
	private static final Pattern MYMEMORY_RESULT =
			Pattern.compile("\"translatedText\":\"((?:\\\\.|[^\"\\\\])*)\"");
	private static final int CACHE_LIMIT = 200;

	private static boolean enabled = false;
	private static final Queue<String> pending = new ConcurrentLinkedQueue<>();
	private static final Map<String, String> cache = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private AutoTranslateModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Translate: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		// Every chat line received (already resolved to plain text) is queued.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, parameters, instant) -> {
			if (!enabled) {
				return;
			}
			String text = message.getString();
			if (text == null || text.length() < 3 || text.length() > 280) {
				return;
			}
			if (pending.size() < 30) {
				pending.add(text);
			}
		});

		Thread worker = new Thread(AutoTranslateModule::workerLoop, "Celarix-AutoTranslate");
		worker.setDaemon(true);
		worker.start();
	}

	private static void workerLoop() {
		while (true) {
			String text = pending.poll();
			if (text == null) {
				sleep(150);
				continue;
			}

			String translated = cache.get(text);
			if (translated == null) {
				translated = translate(text);
				if (translated != null) {
					if (cache.size() >= CACHE_LIMIT) {
						cache.clear();
					}
					cache.put(text, translated);
					sleep(300); // gentle rate limit between real requests
				}
			}

			// Only show when the translation actually differs from the original:
			// already-English messages come back unchanged and are dropped.
			if (translated != null && !translated.equalsIgnoreCase(text.trim())) {
				post(translated);
			}
		}
	}

	private static void post(String translated) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.inGameHud == null) {
			return;
		}
		client.execute(() -> client.inGameHud.getChatHud().addMessage(
				Text.literal("§8[§bTR§8] §f" + translated)));
	}

	// --- Translation providers ----------------------------------------------

	private static String translate(String text) {
		try {
			String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
			String body = http(GTX_ENDPOINT + encoded);
			if (body != null) {
				Matcher matcher = GTX_RESULT.matcher(body);
				if (matcher.find()) {
					return unescape(matcher.group(1));
				}
			}
			// Fallback: MyMemory (attempt auto-detect as the source language).
			body = http(MYMEMORY_ENDPOINT + encoded + "&langpair=Autodetect|en");
			if (body != null) {
				Matcher matcher = MYMEMORY_RESULT.matcher(body);
				if (matcher.find()) {
					String result = unescape(matcher.group(1));
					if (!result.isBlank() && !result.toLowerCase(Locale.ROOT).contains("invalid languagepair")) {
						return result;
					}
				}
			}
		} catch (Exception ignored) {
			// Network trouble: drop this message quietly.
		}
		return null;
	}

	private static String http(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(java.time.Duration.ofSeconds(6))
					.header("User-Agent", "CelarixClient/1.0")
					.GET()
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) {
				return response.body();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/** Minimal JSON string unescape for the pieces we extract. */
	private static String unescape(String value) {
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c != '\\' || i + 1 >= value.length()) {
				out.append(c);
				continue;
			}
			char next = value.charAt(++i);
			switch (next) {
				case 'n' -> out.append('\n');
				case 'r' -> out.append('\r');
				case 't' -> out.append('\t');
				case '"' -> out.append('"');
				case '\\' -> out.append('\\');
				case '/' -> out.append('/');
				case 'u' -> {
					if (i + 4 < value.length()) {
						try {
							out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
							i += 4;
						} catch (NumberFormatException error) {
							out.append('u');
						}
					} else {
						out.append('u');
					}
				}
				default -> out.append(next);
			}
		}
		return out.toString().trim();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}
}