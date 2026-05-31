package kz.itzhiti.revisesft.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import kz.itzhiti.revisesft.revise.ReviseManager;
import kz.itzhiti.revisesft.storage.Lang;
import kz.itzhiti.revisesft.utils.TextUtils;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Command(name = "anydesk", aliases = {"rustdesk"})
@RequiredArgsConstructor
public class RemoteAccessCommand {

    private final ReviseManager reviseManager;

    @Execute
    void executeRemoteAccess(@Context @NotNull Player player, @Arg("код") String code) {
        if (code == null || !code.matches("\\d+")) {
            send(player, "messages.revise.invalid-usage.default");
            return;
        }

        if (!reviseManager.setRemoteModeByTarget(player)) {
            send(player, "messages.revise-player.not-in-revise");
        }
    }

    private void send(Player player, String path) {
        player.sendMessage(TextUtils.parse(Lang.getLang().getString(path, "")));
    }
}
