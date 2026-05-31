package kz.itzhiti.revisesft.command.arguments;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import kz.itzhiti.revisesft.utils.TimeUtil;
import org.bukkit.command.CommandSender;

import java.time.Duration;

public class DurationArgument extends ArgumentResolver<CommandSender, Duration> {

    @Override
    protected ParseResult<Duration> parse(Invocation<CommandSender> invocation, Argument<Duration> context, String argument) {
        int seconds = TimeUtil.parseSeconds(argument);
        if (seconds <= 0) {
            return ParseResult.failure();
        }

        return ParseResult.success(Duration.ofSeconds(seconds));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<Duration> argument, SuggestionContext context) {
        return SuggestionResult.of("30s", "1m", "3m", "5m", "10m", "30m")
                .filterBy(context.getCurrent());
    }
}
