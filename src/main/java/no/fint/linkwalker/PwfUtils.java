package no.fint.linkwalker;

public enum PwfUtils {
    ;

    public static boolean isPwf(String baseUrl) {
        return baseUrl.equals(Constants.PWF_BASE_URL);
    }

}
