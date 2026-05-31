package kz.itzhiti.revisesft.revise;

import kz.itzhiti.revisesft.revise.reason.ReasonLoader;
import kz.itzhiti.revisesft.revise.reason.ReasonResolver;
import kz.itzhiti.revisesft.storage.Config;
import kz.itzhiti.revisesft.storage.Lang;
import kz.itzhiti.revisesft.storage.db.ReviseRepository;
import kz.itzhiti.revisesft.utils.CommandUtil;
import kz.itzhiti.revisesft.utils.LocationUtil;
import kz.itzhiti.revisesft.utils.SoundUtil;
import kz.itzhiti.revisesft.utils.TextUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ReviseManager {

    private static final String UNKNOWN_PLAYER = "Unknown";
    private static final String DEFAULT_CONFESSION_MESSAGE = "У меня чит";
    private static final String DEFAULT_CONFESSION_REASON = "Признание";
    private static final String DEFAULT_CONFESSION_COMMAND = "warn %player% Пункт 4.3 -s";
    private static final String DEFAULT_DISCONNECT_REASON = "Disconnect";
    private static final DateTimeFormatter REWARD_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Getter
    private static ReviseManager instance;

    private final Map<UUID, ReviseSession> sessions = new HashMap<>();
    private final Map<UUID, ReviseSession> moderatorSessions = new HashMap<>();
    private final Map<UUID, String> pendingKickReasons = new HashMap<>();
    private final ReviseRepository repository;
    private final ReasonResolver reasonResolver = new ReasonResolver();
    private final ReasonLoader reasonLoader = new ReasonLoader(reasonResolver);

    public ReviseManager(ReviseRepository repository) {
        instance = this;
        this.repository = repository;
        reasonLoader.reload();
    }

    public StartResult startRevise(Player moderator, Player target, Integer requestedRoom) {
        UUID moderatorUuid = moderator.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        if (sessions.containsKey(targetUuid) || moderatorSessions.containsKey(moderatorUuid)) {
            return StartResult.failed(StartStatus.ALREADY_REVISING);
        }

        Integer roomNumber = requestedRoom == null ? findRandomAvailableRoom() : requestedRoom;
        if (roomNumber == null) {
            return StartResult.failed(StartStatus.NO_AVAILABLE_ROOMS);
        }

        if (!isRoomConfigured(roomNumber)) {
            return StartResult.failed(StartStatus.ROOM_NOT_FOUND);
        }

        if (isRoomOccupied(roomNumber)) {
            return StartResult.failed(StartStatus.ROOM_OCCUPIED);
        }

        ReviseSession session = new ReviseSession(moderatorUuid, targetUuid, roomNumber, this::closeSession);
        sessions.put(targetUuid, session);
        moderatorSessions.put(moderatorUuid, session);
        return new StartResult(StartStatus.STARTED, roomNumber);
    }

    public boolean stopRevise(Player moderator) {
        ReviseSession session = moderatorSessions.get(moderator.getUniqueId());
        if (session == null) {
            return false;
        }

        Player target = Bukkit.getPlayer(session.getTargetUUID());
        if (session.getState() != ReviseState.PENDING) {
            teleportToSpawn(target);
        }
        closeSession(session);
        return true;
    }

    public boolean finishRevise(Player moderator, String inputReason) {
        ReviseSession session = moderatorSessions.get(moderator.getUniqueId());
        if (session == null) {
            return false;
        }

        Player target = Bukkit.getPlayer(session.getTargetUUID());
        ReasonResolver.ResolvedReason resolved = reasonResolver.resolve(inputReason);
        String targetName = target != null ? target.getName() : UNKNOWN_PLAYER;

        repository.saveRevise(
                moderator.getName(),
                targetName,
                resolved.getKey(),
                resolved.getRank(),
                session.getStartTime(),
                System.currentTimeMillis(),
                session.getRoomNumber()
        );

        CommandUtil.dispatchForTarget(
                moderator != null ? moderator : Bukkit.getConsoleSender(),
                target,
                resolved.getActions());
        if (resolved.isClear() && target != null) {
            giveClearReward(target);
            sendSuccessMessage(target);
        }

        teleportToSpawn(target);
        closeSession(session);
        return true;
    }

    public boolean isConfessionMessage(String message) {
        if (message == null) {
            return false;
        }

        List<String> messages = Config.getConfig().getStringList("revise.confession.messages");
        if (messages.isEmpty()) {
            messages = Collections.singletonList(Config.getConfig().getString(
                    "revise.confession.message",
                    DEFAULT_CONFESSION_MESSAGE
            ));
        }

        String normalizedMessage = normalizeConfessionMessage(message);
        return messages.stream()
                .map(this::normalizeConfessionMessage)
                .filter(confession -> !confession.isBlank())
                .anyMatch(confession -> isConfessionMatch(normalizedMessage, confession));
    }

    public boolean finishByConfession(Player target) {
        ReviseSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            return false;
        }

        Player moderator = Bukkit.getPlayer(session.getModeratorUUID());
        ReasonResolver.ResolvedReason resolved = reasonResolver.resolve(
                Config.getConfig().getString("revise.confession.reason", DEFAULT_CONFESSION_REASON)
        );

        repository.saveRevise(
                getPlayerName(session.getModeratorUUID()),
                target.getName(),
                resolved.getKey(),
                resolved.getRank(),
                session.getStartTime(),
                System.currentTimeMillis(),
                session.getRoomNumber()
        );

        CommandUtil.dispatchForTarget(
                moderator != null ? moderator : Bukkit.getConsoleSender(),
                target,
                Collections.singletonList(Config.getConfig().getString("revise.confession.command", DEFAULT_CONFESSION_COMMAND))
        );

        teleportToSpawn(target);
        closeSession(session);
        return true;
    }

    public boolean setRemoteMode(Player moderator) {
        ReviseSession session = moderatorSessions.get(moderator.getUniqueId());
        if (session == null) {
            return false;
        }

        session.setRemoteMode();
        return true;
    }

    public boolean setRemoteModeByTarget(Player target) {
        ReviseSession session = sessions.get(target.getUniqueId());
        if (session == null) {
            return false;
        }

        session.setRemoteMode();
        return true;
    }

    public AddTimeResult addTime(Player moderator, int seconds) {
        ReviseSession session = moderatorSessions.get(moderator.getUniqueId());
        if (session == null) {
            return AddTimeResult.NOT_FOUND;
        }

        if (!session.addTime(seconds, getMaxAdditionalSeconds(moderator))) {
            return AddTimeResult.LIMIT_EXCEEDED;
        }

        return AddTimeResult.ADDED;
    }

    public int getMaxAdditionalSeconds(Player moderator) {
        int fallback = Config.getConfig().getInt("revise.maxAdditionalSeconds", 300);
        ConfigurationSection limits = Config.getConfig().getConfigurationSection("revise.addtime-limits");
        if (limits == null) {
            return fallback;
        }

        int maxSeconds = 0;
        for (String key : limits.getKeys(false)) {
            String path = key + ".";
            int seconds = limits.getInt(path + "seconds", limits.getInt(key, -1));
            String permission = limits.getString(path + "permission", "");
            if (seconds >= 0 && (permission == null || permission.isBlank() || moderator.hasPermission(permission))) {
                maxSeconds = Math.max(maxSeconds, seconds);
            }
        }

        return maxSeconds > 0 ? maxSeconds : fallback;
    }

    public List<String> getFinishReasonSuggestions() {
        return reasonResolver.getSuggestions();
    }

    public List<ActiveRevise> getActiveRevises() {
        return sessions.values().stream()
                .map(this::toActiveRevise)
                .collect(Collectors.toList());
    }

    public void findLogs(String targetName, Consumer<List<ReviseRepository.ReviseLog>> callback) {
        repository.findByTarget(targetName, callback);
    }

    public List<String> getAvailableRoomSuggestions() {
        return getConfiguredRooms().stream()
                .filter(room -> !isRoomOccupied(room))
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    public void handlePlayerActivity(Player player) {
        ReviseSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.onPlayerActivity(player);
        }
    }

    public boolean isPlayerFrozen(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public Location getFrozenLocation(Player player) {
        ReviseSession session = sessions.get(player.getUniqueId());
        Location location = session != null ? session.getFrozenLocation() : null;
        return location != null ? location.clone() : null;
    }

    public boolean isInSession(Player player) {
        UUID uuid = player.getUniqueId();
        return sessions.containsKey(uuid) || moderatorSessions.containsKey(uuid);
    }

    public UUID getModeratorForPlayer(UUID playerUuid) {
        ReviseSession session = sessions.get(playerUuid);
        return session != null ? session.getModeratorUUID() : null;
    }

    public UUID getTargetForModerator(UUID moderatorUuid) {
        ReviseSession session = moderatorSessions.get(moderatorUuid);
        return session != null ? session.getTargetUUID() : null;
    }

    public boolean isSessionRemote(UUID playerUuid) {
        ReviseSession session = sessions.get(playerUuid);
        if (session == null) {
            session = moderatorSessions.get(playerUuid);
        }

        return session != null && session.getState() == ReviseState.REMOTE;
    }

    public void markPlayerKick(Player player) {
        pendingKickReasons.put(player.getUniqueId(), "KICKED");
    }

    public void handlePlayerQuit(Player player) {
        String leaveReason = pendingKickReasons.remove(player.getUniqueId());
        if (leaveReason == null) {
            leaveReason = "DISCONNECTED";
        }

        ReviseSession moderatorSession = moderatorSessions.get(player.getUniqueId());
        if (moderatorSession != null) {
            handleModeratorQuit(moderatorSession);
            return;
        }

        ReviseSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        Player moderator = Bukkit.getPlayer(session.getModeratorUUID());
        SoundUtil.play(moderator, "leave");

        if (session.getState() == ReviseState.STARTED) {
            saveDisconnectLog(session, player);
            executeFreezeQuitPunishment(moderator, player, leaveReason);
            notifyFreezeLeave(moderator, leaveReason);
            closeSession(session);
            return;
        }

        if (session.getState() == ReviseState.REMOTE) {
            notifyRemoteLeave(moderator);
        }
    }

    public void handlePlayerJoin(Player player) {
        ReviseSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            if (session.getState() == ReviseState.PENDING) {
                session.refreshFrozenLocation(player);
            }
            session.restoreBossBar(player);
        }

        ReviseSession moderatorSession = moderatorSessions.get(player.getUniqueId());
        if (moderatorSession != null) {
            moderatorSession.handleModeratorJoin(player);
        }
    }

    public void shutdown() {
        sessions.values().forEach(ReviseSession::cleanup);
        sessions.clear();
        moderatorSessions.clear();
    }

    public ReviseSession getSessionByTarget(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public ReviseSession getSessionByModerator(Player moderator) {
        return moderatorSessions.get(moderator.getUniqueId());
    }

    private Integer findRandomAvailableRoom() {
        List<Integer> rooms = getConfiguredRooms().stream()
                .filter(room -> !isRoomOccupied(room))
                .collect(Collectors.toCollection(ArrayList::new));
        if (rooms.isEmpty()) {
            return null;
        }

        return rooms.get(ThreadLocalRandom.current().nextInt(rooms.size()));
    }

    private List<Integer> getConfiguredRooms() {
        ConfigurationSection rooms = Config.getConfig().getConfigurationSection("revise.rooms");
        if (rooms == null) {
            return Collections.emptyList();
        }

        List<Integer> roomNumbers = new ArrayList<>();
        for (String key : rooms.getKeys(false)) {
            Integer roomNumber = parseRoomNumber(key);
            if (roomNumber != null && isRoomConfigured(roomNumber)) {
                roomNumbers.add(roomNumber);
            }
        }
        return roomNumbers;
    }

    private boolean isRoomConfigured(int roomNumber) {
        return Config.getConfig().contains("revise.rooms." + roomNumber + ".location");
    }

    private boolean isRoomOccupied(int roomNumber) {
        return sessions.values().stream()
                .anyMatch(session -> session.getRoomNumber() == roomNumber);
    }

    private Integer parseRoomNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeConfessionMessage(String message) {
        return message == null ? "" : message.trim();
    }

    private boolean isConfessionMatch(String message, String confession) {
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        String lowerConfession = confession.toLowerCase(Locale.ROOT);
        return lowerMessage.equals(lowerConfession) || lowerMessage.contains(lowerConfession);
    }

    private ActiveRevise toActiveRevise(ReviseSession session) {
        Player target = Bukkit.getPlayer(session.getTargetUUID());
        Player moderator = Bukkit.getPlayer(session.getModeratorUUID());

        return new ActiveRevise(
                target != null ? target.getName() : UNKNOWN_PLAYER,
                moderator != null ? moderator.getName() : UNKNOWN_PLAYER,
                session.getRoomNumber(),
                session.getState(),
                session.getRemainingSeconds()
        );
    }

    private void saveDisconnectLog(ReviseSession session, Player target) {
        ReasonResolver.ResolvedReason resolved = reasonResolver.resolve(
                Config.getConfig().getString("revise.disconnect.reason", DEFAULT_DISCONNECT_REASON)
        );

        repository.saveRevise(
                getPlayerName(session.getModeratorUUID()),
                target.getName(),
                resolved.getKey(),
                resolved.getRank(),
                session.getStartTime(),
                System.currentTimeMillis(),
                session.getRoomNumber()
        );
    }

    private String getPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : UNKNOWN_PLAYER;
    }

    private void closeSession(ReviseSession session) {
        if (session == null) {
            return;
        }

        session.cleanup();
        sessions.remove(session.getTargetUUID());
        moderatorSessions.remove(session.getModeratorUUID());
    }

    private void handleModeratorQuit(ReviseSession session) {
        if (session.getState() == ReviseState.PENDING) {
            closeSession(session);
            return;
        }

        Player target = Bukkit.getPlayer(session.getTargetUUID());
        SoundUtil.play(target, "leave");
        session.startModeratorLeaveMode(() -> stopAfterModeratorLeave(session));
    }

    private void stopAfterModeratorLeave(ReviseSession session) {
        if (session == null || sessions.get(session.getTargetUUID()) != session) {
            return;
        }

        teleportToSpawn(Bukkit.getPlayer(session.getTargetUUID()));
        closeSession(session);
    }

    private void executeFreezeQuitPunishment(Player moderator, Player player, String reason) {
        boolean enabled = Config.getConfig().getStringList("revise.freeze-reasons").stream()
                .anyMatch(configReason -> configReason.equalsIgnoreCase(reason));
        if (!enabled) {
            return;
        }

        String punishCommand = Config.getConfig().getString(
                "revise.freeze-quit-punishment",
                "warn %player% Пункт 4.3 -s"
        ).replace("%player%", player.getName());
        CommandSender sender = moderator != null ? moderator : Bukkit.getConsoleSender();
        CommandUtil.dispatchForTarget(sender, player, List.of(punishCommand));
    }

    private void notifyFreezeLeave(Player moderator, String reason) {
        if (moderator == null) {
            return;
        }

        String message = TextUtils.parse(Lang.getLang().getString("messages.revise.player-leave.freeze.text", "")
                .replace("%reason%", reason));
        if (!message.isEmpty()) {
            moderator.sendMessage(message);
        }
    }

    private void notifyRemoteLeave(Player moderator) {
        if (moderator == null) {
            return;
        }

        String text = TextUtils.parse(Lang.getLang().getString("messages.revise.player-leave.remote.text", ""));
        String button = TextUtils.parse(Lang.getLang().getString("messages.revise.player-leave.remote.button", ""));
        String action = Lang.getLang().getString("messages.revise.player-leave.remote.action", "");
        if (!text.isEmpty()) {
            sendClickableButtonMessage(moderator, text, button, action);
        }
    }

    private void sendClickableButtonMessage(Player player, String text, String button, String action) {
        String[] parts = text.split("%button%", -1);
        TextComponent message = new TextComponent("");
        addLegacyExtra(message, parts[0]);

        ClickEvent clickEvent = null;
        if (action != null && !action.isBlank()) {
            clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, action);
        }
        addLegacyExtra(message, button, clickEvent);

        if (parts.length > 1) {
            addLegacyExtra(message, parts[1]);
        }

        player.spigot().sendMessage(message);
    }

    private void addLegacyExtra(TextComponent message, String text) {
        addLegacyExtra(message, text, null);
    }

    private void addLegacyExtra(TextComponent message, String text, ClickEvent clickEvent) {
        for (BaseComponent component : TextComponent.fromLegacyText(text)) {
            if (clickEvent != null) {
                component.setClickEvent(clickEvent);
            }
            message.addExtra(component);
        }
    }

    private void giveClearReward(Player player) {
        String path = "revise.rewards.clear-any";
        Material material = Material.matchMaterial(Config.getConfig().getString(path + ".material", ""));
        if (material == null) {
            return;
        }

        ItemStack reward = new ItemStack(material);
        ItemMeta meta = reward.getItemMeta();
        if (meta != null) {
            applyRewardMeta(player, path, meta);
            reward.setItemMeta(meta);
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void applyRewardMeta(Player player, String path, ItemMeta meta) {
        String name = Config.getConfig().getString(path + ".name");
        if (name != null) {
            meta.setDisplayName(replaceRewardPlaceholders(player, name));
        }

        List<String> lore = Config.getConfig().getStringList(path + ".lore").stream()
                .map(line -> replaceRewardPlaceholders(player, line))
                .collect(Collectors.toList());
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        applyRewardEnchantments(path, meta);
        if (Config.getConfig().getBoolean(path + ".attributes.HideEnchants", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    private void applyRewardEnchantments(String path, ItemMeta meta) {
        ConfigurationSection enchantments = Config.getConfig().getConfigurationSection(path + ".enchantments");
        if (enchantments == null) {
            return;
        }

        enchantments.getKeys(false).forEach(enchantmentName -> {
            Enchantment enchantment = Enchantment.getByName(enchantmentName);
            if (enchantment != null) {
                meta.addEnchant(enchantment, enchantments.getInt(enchantmentName), true);
            }
        });
    }

    private String replaceRewardPlaceholders(Player player, String value) {
        return TextUtils.parse(value
                .replace("%player%", player.getName())
                .replace("%date%", LocalDate.now().format(REWARD_DATE_FORMAT)));
    }

    private void sendSuccessMessage(Player player) {
        Lang.getLang().getStringList("messages.revise-player.success-finish")
                .forEach(line -> player.sendMessage(TextUtils.parse(line)));
    }

    private void teleportToSpawn(Player player) {
        if (player == null) {
            return;
        }

        Location location = LocationUtil.parse(Config.getConfig().getString("revise.spawn-location"));
        if (location != null) {
            player.teleport(location);
        }
    }

    public enum StartStatus {
        STARTED,
        ALREADY_REVISING,
        ROOM_NOT_FOUND,
        ROOM_OCCUPIED,
        NO_AVAILABLE_ROOMS
    }

    public enum AddTimeResult {
        ADDED,
        NOT_FOUND,
        LIMIT_EXCEEDED
    }

    @Getter
    @RequiredArgsConstructor
    public static final class StartResult {
        private final StartStatus status;
        private final int roomNumber;

        public static StartResult failed(StartStatus status) {
            return new StartResult(status, -1);
        }

        public boolean isStarted() {
            return status == StartStatus.STARTED;
        }
    }

    @Value
    public static class ActiveRevise {
        String target;
        String moderator;
        int roomNumber;
        ReviseState state;
        int remainingSeconds;
    }
}
