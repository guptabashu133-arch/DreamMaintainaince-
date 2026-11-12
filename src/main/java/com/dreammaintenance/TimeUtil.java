package com.dreammaintenance;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([dhms])");

    private TimeUtil() {
    }

    /** "1h30m", "45s", "10m", "2d" -> seconds. Plain number = minutes. -1 if invalid. */
    static long parseSeconds(String input) {
        String s = input.toLowerCase(Locale.ROOT).trim();
        if (s.matches("\\d+")) {
            return Long.parseLong(s) * 60;
        }
        if (!s.matches("(\\d+[dhms])+")) {
            return -1;
        }
        long total = 0;
        Matcher m = PART.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86400;
                case "h" -> n * 3600;
                case "m" -> n * 60;
                default -> n;
            };
        }
        return total > 0 ? total : -1;
    }

    static String format(long seconds) {
        seconds = Math.max(0, seconds);
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (m > 0) {
            sb.append(m).append("m ");
        }
        if (s > 0 || sb.isEmpty()) {
            sb.append(s).append("s");
        }
        return sb.toString().trim();
    }
}
