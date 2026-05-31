package kz.itzhiti.revisesft;

import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.argument.ArgumentKey;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import kz.itzhiti.revisesft.command.arguments.DurationArgument;
import kz.itzhiti.revisesft.command.arguments.FinishReasonArgument;
import kz.itzhiti.revisesft.command.RemoteAccessCommand;
import kz.itzhiti.revisesft.command.ReviseCommand;
import kz.itzhiti.revisesft.command.arguments.StartRoomArgument;
import kz.itzhiti.revisesft.listeners.ReviseListener;
import kz.itzhiti.revisesft.revise.ReviseChatService;
import kz.itzhiti.revisesft.revise.ReviseManager;
import kz.itzhiti.revisesft.storage.Config;
import kz.itzhiti.revisesft.storage.Lang;
import kz.itzhiti.revisesft.storage.db.Database;
import kz.itzhiti.revisesft.storage.db.ReviseRepository;
import kz.itzhiti.revisesft.utils.TextUtils;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashSet;

public final class RevisesFT extends JavaPlugin {

    @Getter
    private static RevisesFT instance;
    private final HashSet<LiteCommands<CommandSender>> commands = new HashSet<>();

    private Database database;
    private ReviseRepository repository;
    private ReviseManager reviseManager;

    @Override
    public void onEnable() {
        instance = this;

        // Загрузка конфигураций
        Config.loadYaml(this);
        Lang.loadYaml(this);

        // Инициализация базы данных
        try {
            database = new Database();
            repository = new ReviseRepository(database);
            getLogger().info(TextUtils.parse("&a> &fБаза данных подключена!"));
        } catch (Exception e) {
            getLogger().severe("Ошибка подключения к базе данных: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reviseManager = new ReviseManager(repository);
        ReviseChatService chatService = new ReviseChatService(reviseManager);

        getServer().getPluginManager().registerEvents(new ReviseListener(reviseManager, chatService), this);

        commands.add(LiteBukkitFactory.builder("revise")
                .argument(Duration.class, new DurationArgument())
                .argument(String.class, ArgumentKey.of(FinishReasonArgument.KEY), new FinishReasonArgument(reviseManager))
                .argument(String.class, ArgumentKey.of(StartRoomArgument.KEY), new StartRoomArgument(reviseManager))
                .commands(new ReviseCommand(reviseManager), new RemoteAccessCommand(reviseManager))
                .message(LiteBukkitMessages.PLAYER_ONLY, input ->
                        TextUtils.parse(Lang.getLang().getString("messages.only-players")))
                .invalidUsage(((invocation, commandSenderInvalidUsage, resultHandlerChain) ->
                                invocation.sender().sendMessage(TextUtils.parse(Lang.getLang().getString("messages.revise.invalid-usage.default")))))
                .missingPermission((invocation, missingPermissions, resultHandlerChain) ->
                        invocation.sender().sendMessage(TextUtils.parse(Lang.getLang().getString("messages.no-permission"))))
                .build());

        getLogger().info(TextUtils.parse("&a> &fПлагин запущен!"));
    }

    @Override
    public void onDisable() {
        // Остановка всех активных проверок
        if (reviseManager != null) {
            reviseManager.shutdown();
        }

        // Закрытие соединений с БД
        if (database != null) {
            database.close();
        }

        // Отмена регистрации команд
        commands.forEach(LiteCommands::unregister);

        getLogger().info(TextUtils.parse("&c> &fПлагин выключен!"));
    }
}
