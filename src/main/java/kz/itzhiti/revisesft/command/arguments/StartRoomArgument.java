package kz.itzhiti.revisesft.command.arguments;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import kz.itzhiti.revisesft.revise.ReviseManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
public class StartRoomArgument extends ArgumentResolver<CommandSender, String> {
    public static final String KEY = "start-room";

    private final ReviseManager reviseManager;

    @Override
    protected ParseResult<String> parse(Invocation<CommandSender> invocation, Argument<String> context, String argument) {
        return ParseResult.success(argument);
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<String> argument, SuggestionContext context) {
        return SuggestionResult.of(reviseManager.getAvailableRoomSuggestions())
                .filterBy(context.getCurrent());
    }
}
