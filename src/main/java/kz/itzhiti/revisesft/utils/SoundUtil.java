package kz.itzhiti.revisesft.utils;

import kz.itzhiti.revisesft.storage.Config;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SoundUtil {

    private static final String SOUND_PATH = "revise.sounds.%s";

    public static void play(UUID playerUuid, String soundKey) {
        play(Bukkit.getPlayer(playerUuid), soundKey);
    }

    public static void play(Player player, String soundKey) {
        if (player == null || soundKey == null) {
            return;
        }

        String path = String.format(SOUND_PATH, soundKey);
        String soundType = Config.getConfig().getString(path + ".type");
        if (soundType == null || soundType.isBlank()) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundType);
            float volume = (float) Config.getConfig().getDouble(path + ".volume", 1.0);
            float pitch = (float) Config.getConfig().getDouble(path + ".pitch", 1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Некорректный звук в конфиге не должен ломать проверку.
        }
    }
}
