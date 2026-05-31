package kz.itzhiti.revisesft.storage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Lang {
    @Getter
    private static FileConfiguration lang;

    public static void loadYaml(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", true);
        }
        Lang.lang = YamlConfiguration.loadConfiguration(file);
    }
}
