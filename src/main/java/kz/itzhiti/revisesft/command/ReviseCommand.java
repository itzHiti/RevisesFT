package kz.itzhiti.revisesft.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.optional.OptionalArg;
import dev.rollczi.litecommands.annotations.permission.Permission;
import kz.itzhiti.revisesft.command.arguments.FinishReasonArgument;
import kz.itzhiti.revisesft.command.arguments.StartRoomArgument;
import kz.itzhiti.revisesft.revise.ReviseManager;
import kz.itzhiti.revisesft.storage.db.ReviseRepository;
import kz.itzhiti.revisesft.storage.Lang;
import kz.itzhiti.revisesft.utils.TextUtils;
import kz.itzhiti.revisesft.utils.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Command(name = "revise")
@Permission("revises.use")
@RequiredArgsConstructor
public class ReviseCommand {

    private static final DateTimeFormatter LOG_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final ReviseManager reviseManager;

    @Execute
    void executeRevise(@Context @NotNull Player player) {
        sendHelp(player);
    }

    @Execute(name = "start")
    void executeReviseStart(
            @Context @NotNull Player moderator,
            @Arg("ник") String targetName,
            @OptionalArg(StartRoomArgument.KEY) String checkingRoomText
    ) {
        Player target = findTarget(moderator, targetName);
        if (target == null) {
            return;
        }
        Integer room = parseRoom(moderator, checkingRoomText);
        if (checkingRoomText != null && !checkingRoomText.isBlank() && room == null) {
            return;
        }

        if (target.equals(moderator)) {
            send(moderator, "messages.revise.self");
            return;
        }

        if (target.hasPermission("revises.exempt")) {
            send(moderator, "messages.revise.cannot-revise", "%player%", target.getName());
            return;
        }

        ReviseManager.StartResult result = reviseManager.startRevise(moderator, target, room);
        if (!result.isStarted()) {
            sendStartError(moderator, result.getStatus(), room);
            return;
        }

        send(moderator, "messages.revise.start",
                "%player%", target.getName(),
                "%room%", String.valueOf(result.getRoomNumber()));
    }

    @Execute(name = "stop")
    void executeReviseStop(@Context @NotNull Player player) {
        if (!reviseManager.stopRevise(player)) {
            send(player, "messages.revise.not-found.report");
            return;
        }

        send(player, "messages.revise.stop");
    }

    @Execute(name = "finish")
    void executeReviseFinish(@Context @NotNull Player player, @Arg(FinishReasonArgument.KEY) String reason) {
        if (reason == null || reason.isBlank()) {
            send(player, "messages.revise.invalid-usage.finish");
            return;
        }

        if (!reviseManager.finishRevise(player, reason)) {
            send(player, "messages.revise.not-found.report");
            return;
        }

        send(player, "messages.revise.finish");
    }

    @Execute(name = "remote")
    @Permission("revises.remote")
    void executeReviseRemote(@Context @NotNull Player player) {
        if (!reviseManager.setRemoteMode(player)) {
            send(player, "messages.revise.not-found.report");
            return;
        }

        String mode = Lang.getLang().getStringList("messages.revise.modes").stream()
                .findFirst()
                .orElse("anydesk");
        send(player, "messages.revise.remote", "%mode%", mode);
    }

    @Execute(name = "addtime")
    void executeReviseAddTime(@Context @NotNull Player player, @Arg Duration time) {
        long durationSeconds = time.getSeconds();
        if (durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE) {
            send(player, "messages.revise.invalid-usage.default");
            return;
        }

        int seconds = (int) durationSeconds;
        int maxAdditionalSeconds = reviseManager.getMaxAdditionalSeconds(player);
        ReviseManager.AddTimeResult result = reviseManager.addTime(player, seconds);
        switch (result) {
            case ADDED -> send(player, "messages.revise.addtime");
            case LIMIT_EXCEEDED -> send(player, "messages.revise.addtime-limited",
                    "%time%", TimeUtil.formatSeconds(maxAdditionalSeconds));
            case NOT_FOUND -> send(player, "messages.revise.not-found.report");
        }
    }

    @Execute(name = "active")
    @Permission("revises.active")
    void executeReviseActive(@Context @NotNull Player player) {
        List<ReviseManager.ActiveRevise> revises = reviseManager.getActiveRevises();
        if (revises.isEmpty()) {
            send(player, "messages.revise.active.none");
            return;
        }

        send(player, "messages.revise.active.header");
        for (int i = 0; i < revises.size(); i++) {
            ReviseManager.ActiveRevise active = revises.get(i);
            sendLines(player, "messages.revise.active.lines",
                    "%number%", String.valueOf(i + 1),
                    "%player%", active.getTarget(),
                    "%room%", String.valueOf(active.getRoomNumber()),
                    "%status%", active.getState().name(),
                    "%time%", TimeUtil.formatSeconds(active.getRemainingSeconds()),
                    "%moderator%", active.getModerator());
        }
    }

    @Execute(name = "logs")
    @Permission("revises.logs")
    void executeReviseLogs(@Context @NotNull Player player, @Arg("ник") String targetName) {
        reviseManager.findLogs(targetName, logs -> sendLogs(player, logs));
    }

    private Player findTarget(Player moderator, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            send(moderator, "messages.revise.not-found.player", "%player%", targetName);
        }
        return target;
    }

    private Integer parseRoom(Player moderator, String roomText) {
        if (roomText == null || roomText.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(roomText);
        } catch (NumberFormatException exception) {
            send(moderator, "messages.revise.invalid-usage.default");
            return null;
        }
    }

    private void sendStartError(Player moderator, ReviseManager.StartStatus status, Integer room) {
        switch (status) {
            case ALREADY_REVISING -> send(moderator, "messages.revise.already-revising");
            case ROOM_NOT_FOUND -> send(moderator, "messages.revise.room-not-found", "%room%", String.valueOf(room));
            case ROOM_OCCUPIED -> send(moderator, "messages.revise.room-occupied", "%room%", String.valueOf(room));
            case NO_AVAILABLE_ROOMS -> send(moderator, "messages.revise.no-available-rooms");
            default -> send(moderator, "messages.revise.invalid-usage.default");
        }
    }

    private void sendHelp(Player player) {
        Lang.getLang().getStringList("messages.revise.help")
                .forEach(line -> player.sendMessage(TextUtils.parse(line)));
    }

    private void sendLogs(Player player, List<ReviseRepository.ReviseLog> logs) {
        if (!player.isOnline()) {
            return;
        }

        if (logs.isEmpty()) {
            send(player, "messages.revise.logs.none");
            return;
        }

        send(player, "messages.revise.logs.header");
        for (int i = 0; i < logs.size(); i++) {
            ReviseRepository.ReviseLog log = logs.get(i);
            sendLines(player, "messages.revise.logs.lines",
                    "%number%", String.valueOf(i + 1),
                    "%player%", log.getTarget(),
                    "%room%", String.valueOf(log.getRoomNumber()),
                    "%time%", TimeUtil.formatTime(log.getEndTime() - log.getStartTime()),
                    "%result%", resolveResult(log),
                    "%rank%", log.getRank(),
                    "%comment%", isClear(log.getReason()) ? "" : displayReason(log.getReason()),
                    "%moderator%", log.getModerator(),
                    "%date%", formatDate(log.getCreatedAt()));
        }
    }

    private String resolveResult(ReviseRepository.ReviseLog log) {
        String reason = log.getReason();
        return isClear(reason)
                ? "&aПройдена"
                : "&cНе пройдена";
    }

    private boolean isClear(String reason) {
        return reason != null && reason.startsWith("revise.finish-reasons.clear.")
                ? true
                : false;
    }

    private String displayReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }

        String reasonPrefix = ".reasons.";
        int reasonIndex = reason.indexOf(reasonPrefix);
        if (reasonIndex >= 0) {
            return reason.substring(reasonIndex + reasonPrefix.length());
        }

        int lastDotIndex = reason.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex + 1 < reason.length()) {
            return reason.substring(lastDotIndex + 1);
        }

        return reason;
    }

    private String formatDate(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(LOG_DATE_FORMAT);
    }

    private void send(Player player, String path, String... placeholders) {
        String message = Lang.getLang().getString(path, "");
        player.sendMessage(TextUtils.parse(applyPlaceholders(message, placeholders)));
    }

    private void sendLines(Player player, String path, String... placeholders) {
        Lang.getLang().getStringList(path).stream()
                .map(line -> applyPlaceholders(line, placeholders))
                .map(TextUtils::parse)
                .forEach(player::sendMessage);
    }

    private String applyPlaceholders(String message, String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }
        return message;
    }
}
