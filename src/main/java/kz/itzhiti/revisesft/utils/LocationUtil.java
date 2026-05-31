package kz.itzhiti.revisesft.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocationUtil {

    public static Location parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.split(",");
        if (parts.length < 4) {
            return null;
        }

        try {
            World world = Bukkit.getWorld(parts[0].trim());
            if (world == null) {
                return null;
            }

            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0;

            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
