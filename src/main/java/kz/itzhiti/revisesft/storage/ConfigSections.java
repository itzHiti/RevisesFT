package kz.itzhiti.revisesft.storage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigSections {

    public static Map<String, ConfigurationSection> children(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<String, ConfigurationSection> children = new LinkedHashMap<>();
        section.getValues(false).forEach((key, value) -> {
            if (value instanceof ConfigurationSection child) {
                children.put(key, child);
            }
        });
        return children;
    }

    public static Map<String, List<String>> stringLists(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> lists = new LinkedHashMap<>();
        section.getValues(false).forEach((key, value) -> {
            if (value instanceof List<?> rawList) {
                List<String> strings = new ArrayList<>();
                rawList.forEach(item -> strings.add(String.valueOf(item)));
                lists.put(key, strings);
            }
        });
        return lists;
    }
}
