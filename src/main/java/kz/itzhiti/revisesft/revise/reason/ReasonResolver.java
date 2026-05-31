package kz.itzhiti.revisesft.revise.reason;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ReasonResolver {
    private static final String DEFAULT_RANK = "Бета";

    private final Map<String, ResolvedReason> reasonMap = new HashMap<>();
    private final Set<String> suggestions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public void clear() {
        reasonMap.clear();
        suggestions.clear();
    }

    public void addReason(String alias, ResolvedReason reason) {
        if (alias == null || alias.isBlank()) {
            return;
        }

        suggestions.add(alias);
        reasonMap.put(normalize(alias), reason);
    }

    public List<String> getSuggestions() {
        return Collections.unmodifiableList(new ArrayList<>(suggestions));
    }

    public ResolvedReason resolve(String input) {
        ResolvedReason resolved = reasonMap.get(normalize(input));
        if (resolved != null) {
            return resolved;
        }

        return ResolvedReason.builder()
                .key(input)
                .canonical(input)
                .rank(DEFAULT_RANK)
                .actions(Collections.emptyList())
                .clear(false)
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase();
    }

    @Value
    @Builder
    public static class ResolvedReason {
        String key;
        String canonical;
        String rank;
        @Singular
        List<String> actions;
        boolean clear;
    }
}
