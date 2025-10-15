package cn.scut.raputa.utils;

import java.security.SecureRandom;

public final class Ids {
    private static final String ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RNG = new SecureRandom();

    private Ids() {
    }

    public static String randomId(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(ALPHANUM.charAt(RNG.nextInt(ALPHANUM.length())));
        return sb.toString();
    }
}
