package kz.itzhiti.revisesft.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    public static String parse(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        try {
            Component parsed = MINI_MESSAGE.deserialize(message);
            message = LEGACY.serialize(parsed);
        } catch (RuntimeException ignored) {
            // Сообщения из старых конфигов могут не быть MiniMessage.
        }

        for (Matcher matcher = HEX_PATTERN.matcher(message); matcher.find(); matcher = HEX_PATTERN.matcher(message)) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace('#', 'x');
            StringBuilder builder = new StringBuilder();
            replaceSharp.chars().forEach(character -> builder.append("&").append((char) character));
            message = message.replace(hexCode, builder.toString());
        }

        return ChatColor.translateAlternateColorCodes('&', message).replace("&", "");
    }

    public static String parse(List<String> messages) {
        StringBuilder builder = new StringBuilder();
        for (String message : messages) {
            builder.append(parse(message)).append("\n");
        }
        return builder.toString().trim();
    }

    public static Component createCopyableMessage(String formattedMessage, String originalMessage, String hoverText) {
        Component component = LEGACY.deserialize(formattedMessage);

        if (hoverText != null && !hoverText.isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hoverText)));
        }

        return component.clickEvent(ClickEvent.copyToClipboard(originalMessage));
    }

    public static void sendComponent(Player player, Component component) {
        player.sendMessage(LEGACY.serialize(component));
    }

    public static void sendCopyableMessage(Player player, String formattedMessage, String originalMessage, String hoverText) {
        sendComponent(player, createCopyableMessage(formattedMessage, originalMessage, hoverText));
    }
}
