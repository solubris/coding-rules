package com.solubris.enforcer;

public class PropertyUtil {
    private PropertyUtil() {
    }

    public static String asPlaceHolder(String version) {
        return String.format("${%s}", version);
    }

    public static String fromPlaceHolder(String version) {
        return version.substring(2, version.length() - 1);
    }
}
