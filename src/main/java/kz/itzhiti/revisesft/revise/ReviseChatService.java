package kz.itzhiti.revisesft.revise;

import kz.itzhiti.revisesft.storage.Lang;
import kz.itzhiti.revisesft.utils.SoundUtil;
import kz.itzhiti.revisesft.utils.TextUtils;
import kz.itzhiti.revisesft.utils.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class ReviseChatService {

    private static final String MODERATOR_PUBLIC_CHAT_PREFIX = "!";
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "/msg", "/tell", "/w", "/whisper", "/r", "/reply"
    );

    private final ReviseManager reviseManager;

    public boolean handleCommand(Player sender, String rawCommand) {
        RemoteAccessInput remoteAccessInput = parseRemoteAccessCommand(rawCommand);
        if (remoteAccessInput != null && !reviseManager.isInSession(sender)) {
            send(sender, "messages.revise-player.not-in-revise");
            return true;
        }

        if (!reviseManager.isInSession(sender)) {
            return false;
        }

        if (isModerator(sender)) {
            return false;
        }

        if (remoteAccessInput != null) {
            return handleRemoteAccessCommand(sender, remoteAccessInput);
        }

        if (isRemoteTargetSession(sender)) {
            relay(sender, rawCommand, true);
            return true;
        }

        reviseManager.handlePlayerActivity(sender);
        if (isAllowedCommand(rawCommand)) {
            return false;
        }

        relay(sender, rawCommand, true);
        return true;
    }

    public void handleChat(Player sender, String message) {
        if (!reviseManager.isInSession(sender)) {
            return;
        }

        if (isPendingModeratorSession(sender)) {
            send(sender, "messages.revise.not-started");
            return;
        }

        reviseManager.handlePlayerActivity(sender);
        if (reviseManager.isConfessionMessage(message) && reviseManager.finishByConfession(sender)) {
            return;
        }

        if (isTargetOnCooldown(sender)) {
            return;
        }

        relay(sender, message, false);
    }

    public boolean isModeratorPublicChat(Player sender, String message) {
        return isModerator(sender) && message.startsWith(MODERATOR_PUBLIC_CHAT_PREFIX);
    }

    public String stripModeratorPublicChatPrefix(String message) {
        if (message.length() <= MODERATOR_PUBLIC_CHAT_PREFIX.length()) {
            return message;
        }

        return message.substring(MODERATOR_PUBLIC_CHAT_PREFIX.length()).stripLeading();
    }

    private RemoteAccessInput parseRemoteAccessCommand(String rawCommand) {
        String[] parts = rawCommand.trim().split("\\s+", 3);
        if (parts.length == 0 || !parts[0].startsWith("/")) {
            return null;
        }

        String command = parts[0].substring(1).toLowerCase(Locale.ROOT);
        int namespaceIndex = command.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex + 1 < command.length()) {
            command = command.substring(namespaceIndex + 1);
        }

        if (!command.equals("anydesk") && !command.equals("rustdesk")) {
            return null;
        }

        String code = parts.length == 2 ? parts[1] : "";
        return new RemoteAccessInput(command, code);
    }

    private boolean handleRemoteAccessCommand(Player sender, RemoteAccessInput input) {
        if (!input.code().matches("\\d+")) {
            send(sender, "messages.revise.invalid-usage.default");
            return true;
        }

        ReviseSession session = reviseManager.getSessionByTarget(sender);
        if (session == null) {
            send(sender, "messages.revise-player.not-in-revise");
            return true;
        }

        if (session.getState() == ReviseState.PENDING) {
            reviseManager.handlePlayerActivity(sender);
        }

        if (!reviseManager.setRemoteModeByTarget(sender)) {
            send(sender, "messages.revise-player.not-in-revise");
            return true;
        }

        sendRemoteModeToModerator(sender, input.mode());
        relay(sender, formatRemoteAccessMessage(input), true);
        return true;
    }

    private void sendRemoteModeToModerator(Player target, String mode) {
        UUID moderatorUuid = reviseManager.getModeratorForPlayer(target.getUniqueId());
        if (moderatorUuid == null) {
            return;
        }

        Player moderator = Bukkit.getPlayer(moderatorUuid);
        if (moderator != null && moderator.isOnline()) {
            send(moderator, "messages.revise.remote", "%mode%", mode);
        }
    }

    private String formatRemoteAccessMessage(RemoteAccessInput input) {
        return "#" + input.mode().toUpperCase(Locale.ROOT) + "(" + input.code() + ")";
    }

    private boolean isModerator(Player sender) {
        return reviseManager.getTargetForModerator(sender.getUniqueId()) != null;
    }

    private boolean isPendingModeratorSession(Player sender) {
        ReviseSession session = reviseManager.getSessionByModerator(sender);
        return session != null && session.getState() == ReviseState.PENDING;
    }

    private boolean isRemoteTargetSession(Player sender) {
        ReviseSession session = reviseManager.getSessionByTarget(sender);
        return session != null && session.getState() == ReviseState.REMOTE;
    }

    private boolean isTargetOnCooldown(Player sender) {
        ReviseSession session = reviseManager.getSessionByTarget(sender);
        if (session == null) {
            return false;
        }

        if (session.canSendMessage()) {
            session.updateLastMessageTime();
            return false;
        }

        send(sender, "messages.revise-player.cooldown", "%time%", TimeUtil.formatTime(session.getRemainingCooldown()));
        return true;
    }

    private boolean isAllowedCommand(String rawCommand) {
        String[] parts = rawCommand.trim().toLowerCase().split("\\s+", 2);
        return parts.length > 0 && ALLOWED_COMMANDS.contains(parts[0]);
    }

    private void relay(Player sender, String message, boolean command) {
        UUID targetUuid = reviseManager.getTargetForModerator(sender.getUniqueId());
        if (targetUuid != null) {
            relayFromModerator(sender, targetUuid, message);
            return;
        }

        UUID moderatorUuid = reviseManager.getModeratorForPlayer(sender.getUniqueId());
        if (moderatorUuid != null) {
            relayFromTarget(sender, moderatorUuid, message, command);
        }
    }

    private void relayFromModerator(Player moderator, UUID targetUuid, String message) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            send(moderator, "messages.chat.not-sent");
            return;
        }
        String hoverText = Lang.getLang().getString("messages.chat.hover.copy", "&7Нажмите, чтобы скопировать");

        sendCopyable(
                moderator,
                "messages.chat.moderator.chat-from",
                target.getName(),
                message,
                hoverText
        );

        target.sendMessage(format("messages.chat.target.chat-from", target.getName(), message));
        SoundUtil.play(target, "chat");
    }

    private void relayFromTarget(Player target, UUID moderatorUuid, String message, boolean command) {
        Player moderator = Bukkit.getPlayer(moderatorUuid);
        if (moderator == null || !moderator.isOnline()) {
            send(target, "messages.chat.not-sent");
            return;
        }

        if (!command) {
            target.sendMessage(format("messages.chat.target.chat", target.getName(), message));
        }

        String hoverText = Lang.getLang().getString("messages.chat.hover.copy", "&7Нажмите, чтобы скопировать");
        sendCopyable(moderator, "messages.chat.moderator.chat", target.getName(), message, hoverText);
        SoundUtil.play(moderator, "chat");
    }

    private void sendCopyable(Player player, String path, String playerName, String message, String hoverText) {
        TextUtils.sendCopyableMessage(player, format(path, playerName, message), message, hoverText);
    }

    private String format(String path, String playerName, String message) {
        return TextUtils.parse(Lang.getLang().getString(path, "")
                .replace("%player%", playerName)
                .replace("%message%", message));
    }

    private void send(Player player, String path) {
        send(player, path, new String[0]);
    }

    private void send(Player player, String path, String... placeholders) {
        String message = Lang.getLang().getString(path, "");
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }
        player.sendMessage(TextUtils.parse(message));
    }

    private record RemoteAccessInput(String mode, String code) {
    }
}
