package kz.itzhiti.revisesft.revise.reason;

import kz.itzhiti.revisesft.storage.Config;
import kz.itzhiti.revisesft.storage.ConfigSections;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class ReasonLoader {

    private static final String ROOT = "revise.finish-reasons";
    private static final String DEFAULT_RANK = "Бета";

    private final ReasonResolver resolver;

    public void reload() {
        resolver.clear();
        loadClearReasons();
        loadPunishmentReasons("warn");
        loadPunishmentReasons("tempban");
        loadPunishmentReasons("ban");
    }

    private void loadClearReasons() {
        ConfigurationSection section = Config.getConfig().getConfigurationSection(ROOT + ".clear");
        ConfigSections.stringLists(section).forEach((rank, aliases) -> {
            ReasonResolver.ResolvedReason reason = ReasonResolver.ResolvedReason.builder()
                    .key(ROOT + ".clear." + rank)
                    .canonical("чист(" + rank + ")")
                    .rank(rank)
                    .clear(true)
                    .build();

            aliases.forEach(alias -> resolver.addReason(alias, reason));
        });
    }

    private void loadPunishmentReasons(String type) {
        ConfigurationSection section = Config.getConfig().getConfigurationSection(ROOT + "." + type);
        ConfigSections.children(section).forEach((key, child) ->
                loadPunishmentNode(type, singlePath(key), child, Collections.emptyList()));
    }

    private void loadPunishmentNode(String type, List<String> path, ConfigurationSection section, List<String> inheritedActions) {
        List<String> actions = resolveActions(section, inheritedActions);
        String key = ROOT + "." + type + "." + String.join(".", path);
        String fallbackCanonical = path.get(path.size() - 1);

        registerReasonNode(key, fallbackCanonical, section, actions);

        ConfigSections.children(section).forEach((childKey, childSection) -> {
            if ("reasons".equalsIgnoreCase(childKey)) {
                return;
            }

            List<String> childPath = new ArrayList<>(path);
            childPath.add(childKey);
            loadPunishmentNode(type, childPath, childSection, actions);
        });
    }

    private void registerReasonNode(String key, String fallbackCanonical, ConfigurationSection section, List<String> actions) {
        ConfigurationSection reasons = section.getConfigurationSection("reasons");
        if (reasons != null) {
            ConfigSections.stringLists(reasons).forEach((canonical, aliases) ->
                    registerReason(key + ".reasons." + canonical, canonical, aliases, actions));
            return;
        }

        List<String> aliases = section.getStringList("reasons");
        boolean hasReasonData = section.contains("reason") || !aliases.isEmpty();
        boolean hasActionData = section.contains("action") || section.contains("actions");
        if (!hasReasonData && !hasActionData) {
            return;
        }

        String canonical = section.getString("reason", firstOrFallback(aliases, fallbackCanonical));
        registerReason(key, canonical, aliases.isEmpty() ? Collections.singletonList(canonical) : aliases, actions);
    }

    private void registerReason(String key, String canonical, List<String> aliases, List<String> actions) {
        ReasonResolver.ResolvedReason reason = punishmentReason(key, canonical, actions);
        aliases.forEach(alias -> resolver.addReason(alias, reason));
    }

    private List<String> resolveActions(ConfigurationSection section, List<String> inheritedActions) {
        List<String> actions = new ArrayList<>();
        String action = section.getString("action");
        if (action != null && !action.isBlank()) {
            actions.add(action);
        }
        actions.addAll(section.getStringList("actions"));

        return actions.isEmpty() ? inheritedActions : actions;
    }

    private List<String> singlePath(String key) {
        return Collections.singletonList(key);
    }

    private ReasonResolver.ResolvedReason punishmentReason(String key, String canonical, List<String> actions) {
        return ReasonResolver.ResolvedReason.builder()
                .key(key)
                .canonical(canonical)
                .rank(DEFAULT_RANK)
                .actions(actions)
                .clear(false)
                .build();
    }

    private String firstOrFallback(List<String> values, String fallback) {
        return values.isEmpty() ? fallback : values.get(0);
    }
}
