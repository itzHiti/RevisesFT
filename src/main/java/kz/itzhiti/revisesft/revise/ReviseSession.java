package kz.itzhiti.revisesft.revise;

import kz.itzhiti.revisesft.RevisesFT;
import kz.itzhiti.revisesft.storage.Config;
import kz.itzhiti.revisesft.storage.Lang;
import kz.itzhiti.revisesft.utils.CommandUtil;
import kz.itzhiti.revisesft.utils.LocationUtil;
import kz.itzhiti.revisesft.utils.SoundUtil;
import kz.itzhiti.revisesft.utils.TextUtils;
import kz.itzhiti.revisesft.utils.TimeUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Getter
public class ReviseSession {
    private final UUID moderatorUUID;
    private final UUID targetUUID;
    private final int roomNumber;
    private final long startTime;
    private final Consumer<ReviseSession> closeHandler;

    @Setter
    private ReviseState state;
    private int remainingSeconds;
    private int totalSeconds;
    private int additionalSeconds;

    private BossBar moderatorBar;
    private BossBar targetBar;

    private BukkitTask timerTask;
    private BukkitTask messageTask;
    private BukkitTask moderatorLeaveTask;

    private Location frozenLocation;
    private boolean hasMoved = false;
    private int moderatorLeaveRemainingSeconds;
    private int moderatorLeaveTotalSeconds;

    private long lastMessageTime = 0;
    private static final long MESSAGE_COOLDOWN = 2000; // 2 секунды

    public ReviseSession(UUID moderatorUUID, UUID targetUUID, int roomNumber, Consumer<ReviseSession> closeHandler) {
        this.moderatorUUID = moderatorUUID;
        this.targetUUID = targetUUID;
        this.roomNumber = roomNumber;
        this.closeHandler = closeHandler;
        this.startTime = System.currentTimeMillis();
        this.state = ReviseState.PENDING;
        this.remainingSeconds = Config.getConfig().getInt("revise.defaultSeconds", 300);
        this.totalSeconds = remainingSeconds;
        this.additionalSeconds = 0;

        createBossBars();
        startPendingMode();
    }

    private void createBossBars() {
        // Модератор - показываем сразу
        String modColorStr = Lang.getLang().getString("bossbar.moderator.values.color", "RED");
        String modStyleStr = Lang.getLang().getString("bossbar.moderator.values.style", "SOLID");
        BarColor modColor = parseColor(modColorStr);
        BarStyle modStyle = parseStyle(modStyleStr);

        String modTitle = TextUtils.parse(Lang.getLang().getString("bossbar.moderator.title.pending"));
        moderatorBar = Bukkit.createBossBar(modTitle, modColor, modStyle);
        moderatorBar.setProgress(1.0);

        Player moderator = Bukkit.getPlayer(moderatorUUID);
        if (moderator != null) {
            moderatorBar.addPlayer(moderator);
        }

        // Цель - создаём, но НЕ показываем в PENDING
        String targetColorStr = Lang.getLang().getString("bossbar.target.values.color", "RED");
        String targetStyleStr = Lang.getLang().getString("bossbar.target.values.style", "SOLID");
        BarColor targetColor = parseColor(targetColorStr);
        BarStyle targetStyle = parseStyle(targetStyleStr);

        String targetTitle = TextUtils.parse(Lang.getLang().getString("bossbar.target.title.started", "")
                .replace("%time%", formatTime(remainingSeconds)));
        targetBar = Bukkit.createBossBar(targetTitle, targetColor, targetStyle);
        targetBar.setProgress(1.0);

        // НЕ добавляем игрока в PENDING режиме!
    }

    private void startPendingMode() {
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            frozenLocation = target.getLocation();
        }
    }

    // Вызывается при ЛЮБОЙ активности игрока (движение, чат, взаимодействие)
    public void onPlayerActivity(Player player) {
        if (!player.getUniqueId().equals(targetUUID)) return;
        if (state != ReviseState.PENDING) return;
        if (hasMoved) return;

        hasMoved = true;
        freezePlayer(player);

        // ТЕПЕРЬ показываем боссбар игроку
        if (targetBar != null) {
            targetBar.addPlayer(player);
        }

        startTimer();
        startMessageTask();

        // Звук для модератора
        SoundUtil.play(moderatorUUID, "start");
    }

    private void freezePlayer(Player player) {
        // Телепортация в комнату проверки (если указано в конфиге)
        if (Config.getConfig().contains("revise.rooms." + roomNumber + ".location")) {
            String locStr = Config.getConfig().getString("revise.rooms." + roomNumber + ".location");
            Location loc = LocationUtil.parse(locStr);
            if (loc != null) {
                player.teleport(loc);
                frozenLocation = loc;
            }
        }
    }

    private void startTimer() {
        state = ReviseState.STARTED;
        int defaultSeconds = Config.getConfig().getInt("revise.defaultSeconds", 300);
        remainingSeconds = defaultSeconds + additionalSeconds;
        totalSeconds = remainingSeconds;
        startTimerTask();
    }

    private void startTimerTask() {
        if (timerTask != null) {
            return;
        }

        timerTask = Bukkit.getScheduler().runTaskTimer(RevisesFT.getInstance(), () -> {
            if (state != ReviseState.STARTED) return;

            remainingSeconds--;
            updateBossBars();

            if (remainingSeconds <= 0) {
                timeExpired();
            }
        }, 20L, 20L);
    }

    private void startMessageTask() {
        int period = Lang.getLang().getInt("messages.revise-player.start.period", 2);

        messageTask = Bukkit.getScheduler().runTaskTimer(RevisesFT.getInstance(), () -> {
            if (state == ReviseState.REMOTE) {
                if (messageTask != null) {
                    messageTask.cancel();
                }
                return;
            }

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                Lang.getLang().getStringList("messages.revise-player.start.message")
                        .forEach(line -> target.sendMessage(TextUtils.parse(line)));
            }
        }, 0L, period * 20L);
    }

    private void updateBossBars() {
        String timeStr = formatTime(remainingSeconds);
        double progress = Math.max(0.0, Math.min(1.0, (double) remainingSeconds / Math.max(1, totalSeconds)));
        String modTitleTemplate = Lang.getLang().getString("bossbar.moderator.title.started", "");
        String targetTitleTemplate = Lang.getLang().getString("bossbar.target.title.started", "");

        if (moderatorBar != null && !modTitleTemplate.isEmpty()) {
            String modTitle = TextUtils.parse(modTitleTemplate.replace("%time%", timeStr));
            moderatorBar.setTitle(modTitle);
            moderatorBar.setProgress(progress);
            moderatorBar.setVisible(true);
        }

        if (targetBar != null && !targetTitleTemplate.isEmpty()) {
            String targetTitle = TextUtils.parse(targetTitleTemplate.replace("%time%", timeStr));
            targetBar.setTitle(targetTitle);
            targetBar.setProgress(progress);
            targetBar.setVisible(true);
        }
    }

    public void restoreBossBar(Player player) {
        if (player == null) {
            return;
        }

        if (player.getUniqueId().equals(moderatorUUID)) {
            if (moderatorBar != null) {
                moderatorBar.addPlayer(player);
            }
            return;
        }

        if (player.getUniqueId().equals(targetUUID) && state != ReviseState.PENDING && targetBar != null) {
            targetBar.addPlayer(player);
        }
    }

    public void startModeratorLeaveMode(Runnable timeoutAction) {
        if (moderatorLeaveTask != null || state == ReviseState.PENDING) {
            return;
        }

        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        moderatorLeaveTotalSeconds = Math.max(1, Config.getConfig().getInt("revise.time-after-leave", 150));
        moderatorLeaveRemainingSeconds = moderatorLeaveTotalSeconds;
        applyModeratorLeaveBarValues();

        moderatorLeaveTask = Bukkit.getScheduler().runTaskTimer(RevisesFT.getInstance(), () -> {
            if (moderatorLeaveRemainingSeconds <= 0) {
                if (moderatorLeaveTask != null) {
                    moderatorLeaveTask.cancel();
                    moderatorLeaveTask = null;
                }
                timeoutAction.run();
                return;
            }

            updateModeratorLeaveBossBars();
            moderatorLeaveRemainingSeconds--;
        }, 0L, 20L);
    }

    public void handleModeratorJoin(Player moderator) {
        if (moderatorBar != null && moderator != null) {
            moderatorBar.addPlayer(moderator);
        }

        if (moderatorLeaveTask != null) {
            moderatorLeaveTask.cancel();
            moderatorLeaveTask = null;
        }

        restoreCurrentBossBars();

        if (state == ReviseState.STARTED) {
            startTimerTask();
        }
    }

    private void restoreCurrentBossBars() {
        if (state == ReviseState.STARTED) {
            applyDefaultBarValues();
            updateBossBars();
            return;
        }

        if (state == ReviseState.REMOTE) {
            applyDefaultBarValues();
            updateRemoteBossBars();
        }
    }

    private void applyDefaultBarValues() {
        if (moderatorBar != null) {
            moderatorBar.setColor(parseColor(Lang.getLang().getString("bossbar.moderator.values.color", "RED")));
            moderatorBar.setStyle(parseStyle(Lang.getLang().getString("bossbar.moderator.values.style", "SOLID")));
            moderatorBar.setVisible(true);
        }

        if (targetBar != null) {
            targetBar.setColor(parseColor(Lang.getLang().getString("bossbar.target.values.color", "RED")));
            targetBar.setStyle(parseStyle(Lang.getLang().getString("bossbar.target.values.style", "SOLID")));
            targetBar.setVisible(true);
        }
    }

    private void applyModeratorLeaveBarValues() {
        BarColor color = parseColor(Lang.getLang().getString("bossbar.mod-left.values.color", "WHITE"));
        BarStyle style = parseStyle(Lang.getLang().getString("bossbar.mod-left.values.style", "SOLID"));

        if (moderatorBar != null) {
            moderatorBar.setColor(color);
            moderatorBar.setStyle(style);
            moderatorBar.setVisible(true);
        }

        if (targetBar != null) {
            targetBar.setColor(color);
            targetBar.setStyle(style);
            targetBar.setVisible(true);
        }
    }

    private void updateModeratorLeaveBossBars() {
        String titleTemplate = Lang.getLang().getString("bossbar.mod-left.title", "&eОжидайте %time%");
        String title = TextUtils.parse(titleTemplate.replace("%time%", formatTime(moderatorLeaveRemainingSeconds)));
        double progress = Math.max(0.0, Math.min(1.0,
                (double) moderatorLeaveRemainingSeconds / Math.max(1, moderatorLeaveTotalSeconds)));

        if (moderatorBar != null) {
            moderatorBar.setTitle(title);
            moderatorBar.setProgress(progress);
            moderatorBar.setVisible(true);
        }

        if (targetBar != null) {
            targetBar.setTitle(title);
            targetBar.setProgress(progress);
            targetBar.setVisible(true);
            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                targetBar.addPlayer(target);
            }
        }
    }

    public void setRemoteMode() {
        if (state == ReviseState.REMOTE) return;

        state = ReviseState.REMOTE;

        // Звук для модератора
        SoundUtil.play(moderatorUUID, "remote");

        updateRemoteBossBars();

        // Обновление боссбаров
        Player moderator = Bukkit.getPlayer(moderatorUUID);
        if (moderator != null) {
            Player target = Bukkit.getPlayer(targetUUID);
            String targetName = target != null ? target.getName() : "Unknown";
            String modTitleTemplate = Lang.getLang().getString("bossbar.moderator.title.remote", "");
            if (!modTitleTemplate.isEmpty()) {
                String modTitle = TextUtils.parse(modTitleTemplate.replace("%player%", targetName));
                moderatorBar.setTitle(modTitle);
                moderatorBar.setProgress(1.0);
            }
        }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            String targetTitleTemplate = Lang.getLang().getString("bossbar.target.title.remote", "");
            if (!targetTitleTemplate.isEmpty()) {
                String targetTitle = TextUtils.parse(targetTitleTemplate);
                targetBar.setTitle(targetTitle);
                targetBar.setProgress(1.0);
            }
        }

        // Остановка таймера сообщений
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        if (messageTask != null) {
            messageTask.cancel();
            messageTask = null;
        }
    }

    private void updateRemoteBossBars() {
        Player moderator = Bukkit.getPlayer(moderatorUUID);
        Player target = Bukkit.getPlayer(targetUUID);
        String targetName = target != null ? target.getName() : "Unknown";

        String modTitleTemplate = Lang.getLang().getString("bossbar.moderator.title.remote", "");
        if (moderatorBar != null && !modTitleTemplate.isEmpty()) {
            String modTitle = TextUtils.parse(modTitleTemplate.replace("%player%", targetName));
            moderatorBar.setTitle(modTitle);
            moderatorBar.setProgress(1.0);
            moderatorBar.setVisible(true);
            if (moderator != null) {
                moderatorBar.addPlayer(moderator);
            }
        }

        String targetTitleTemplate = Lang.getLang().getString("bossbar.target.title.remote", "");
        if (targetBar != null && !targetTitleTemplate.isEmpty()) {
            String targetTitle = TextUtils.parse(targetTitleTemplate);
            targetBar.setTitle(targetTitle);
            targetBar.setProgress(1.0);
            targetBar.setVisible(true);
            if (target != null) {
                targetBar.addPlayer(target);
            }
        }
    }

    public boolean addTime(int seconds, int maxAdditionalSeconds) {
        if (seconds <= 0 || additionalSeconds + seconds > maxAdditionalSeconds) {
            return false;
        }

        additionalSeconds += seconds;
        remainingSeconds += seconds;
        totalSeconds += seconds;
        if (state == ReviseState.STARTED) {
            updateBossBars();
        } else if (state == ReviseState.REMOTE) {
            updateRemoteBossBars();
        }

        // Звук для модератора
        SoundUtil.play(moderatorUUID, "addtime");

        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            String timeStr = formatTime(seconds);
            String message = TextUtils.parse(Lang.getLang().getString("messages.revise-player.addtime", "")
                    .replace("%time%", timeStr));
            if (!message.isEmpty()) {
                target.sendMessage(message);
            }
        }

        return true;
    }

    private void timeExpired() {
        Player target = Bukkit.getPlayer(targetUUID);
        Player moderator = Bukkit.getPlayer(moderatorUUID);
        if (target != null) {
            String punishCommand = Config.getConfig().getString("revise.timeout-punishment",
                    "warn %player% Пункт 4.3 -s").replace("%player%", target.getName());
            CommandUtil.dispatchForPlayer(moderator, List.of(punishCommand));
        }

        closeHandler.accept(this);
    }

    public void cleanup() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        if (messageTask != null) {
            messageTask.cancel();
            messageTask = null;
        }

        if (moderatorLeaveTask != null) {
            moderatorLeaveTask.cancel();
            moderatorLeaveTask = null;
        }

        if (moderatorBar != null) {
            moderatorBar.removeAll();
            moderatorBar = null;
        }

        if (targetBar != null) {
            targetBar.removeAll();
            targetBar = null;
        }
    }

    private String formatTime(int seconds) {
        return TimeUtil.formatSeconds(seconds);
    }

    private BarColor parseColor(String str) {
        try {
            return BarColor.valueOf(str.toUpperCase());
        } catch (Exception e) {
            return BarColor.RED;
        }
    }

    private BarStyle parseStyle(String str) {
        try {
            return BarStyle.valueOf(str.toUpperCase());
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    public Player getModerator() {
        return Bukkit.getPlayer(moderatorUUID);
    }

    public Player getTarget() {
        return Bukkit.getPlayer(targetUUID);
    }

    public boolean canSendMessage() {
        return System.currentTimeMillis() - lastMessageTime >= MESSAGE_COOLDOWN;
    }

    public long getRemainingCooldown() {
        long elapsed = System.currentTimeMillis() - lastMessageTime;
        return Math.max(0, MESSAGE_COOLDOWN - elapsed);
    }

    public void updateLastMessageTime() {
        this.lastMessageTime = System.currentTimeMillis();
    }

    public void refreshFrozenLocation(Player player) {
        if (player != null && player.getUniqueId().equals(targetUUID)) {
            frozenLocation = player.getLocation();
        }
    }
}

