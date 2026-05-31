package kz.itzhiti.revisesft.utils;

import kz.itzhiti.revisesft.RevisesFT;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DiscordWebhook {

    public static void sendAsync(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(RevisesFT.getInstance(), () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String json = "{\"content\": " + toJsonString(content) + "}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getInputStream().close();
                conn.disconnect();
            } catch (Exception ignored) {
                // Вебхук не должен останавливать игровой сценарий проверки.
            }
        });
    }

    private static String toJsonString(String s) {
        if (s == null) return "null";
        // minimal escape
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return '"' + esc + '"';
    }
}

