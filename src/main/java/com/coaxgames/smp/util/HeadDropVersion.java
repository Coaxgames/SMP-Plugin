package com.coaxgames.smp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeadDropVersion {
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+[A-Za-z0-9.-]+)?$");

    private HeadDropVersion() {
    }

    public static boolean isSupported(String version) {
        if (version == null) {
            return false;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            return false;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            return major > 3 || major == 3 && minor >= 1;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
