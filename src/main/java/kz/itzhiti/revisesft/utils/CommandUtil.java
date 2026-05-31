package kz.itzhiti.revisesft.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommandUtil {

    public static void dispatchForPlayer(Player player, List<String> commands) {
        if (player == null || commands == null || commands.isEmpty()) {
            return;
        }

        commands.stream()
                .filter(command -> command != null && !command.isBlank())
                .map(command -> command.replace("%player%", player.getName()))
                .map(CommandUtil::normalizeCommand)
                .forEach(command -> Bukkit.dispatchCommand(player, command));
    }

    public static void dispatchForTarget(CommandSender sender, Player target, List<String> commands) {
        if (sender == null || target == null || commands == null || commands.isEmpty()) {
            return;
        }

        commands.stream()
                .filter(command -> command != null && !command.isBlank())
                .map(command -> command.replace("%player%", target.getName()))
                .map(CommandUtil::normalizeCommand)
                .forEach(command -> Bukkit.dispatchCommand(sender, command));
    }

    private static String normalizeCommand(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
