package no.fint.linkwalker;

import org.apache.commons.lang3.StringUtils;

public enum PwfUtils {
    ;

    public static boolean isPwf(String location) {
        return StringUtils.startsWith(location, Constants.PWF_BASE_URL);
    }

}
