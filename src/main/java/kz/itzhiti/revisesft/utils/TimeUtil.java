package kz.itzhiti.revisesft.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeUtil {

    public static int parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }

        input = input.trim().toLowerCase();
        try {
            if (input.endsWith("ms")) {
                double ms = Double.parseDouble(input.substring(0, input.length() - 2));
                return (int) Math.ceil(ms / 1000.0);
            }
            if (input.endsWith("s")) {
                return (int) Double.parseDouble(input.substring(0, input.length() - 1));
            }
            if (input.endsWith("m")) {
                return (int) (Double.parseDouble(input.substring(0, input.length() - 1)) * 60);
            }
            if (input.endsWith("h")) {
                return (int) (Double.parseDouble(input.substring(0, input.length() - 1)) * 3600);
            }
            if (input.endsWith("d")) {
                return (int) (Double.parseDouble(input.substring(0, input.length() - 1)) * 86400);
            }
            // число секунд
            return (int) Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatSeconds(int seconds) {
        if (seconds <= 0) {
            return "0с";
        }

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("ч ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(" мин ");
        }
        if (secs > 0 || sb.length() == 0) {
            sb.append(secs).append(" сек");
        }

        return sb.toString().trim();
    }

    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;

        if (seconds < 60) {
            return seconds + " сек";
        }

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (remainingSeconds == 0) {
            return minutes + " мин";
        }

        return minutes + " мин " + remainingSeconds + " сек";
    }

}

