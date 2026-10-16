package com.vayomitra.security;

import org.mindrot.jbcrypt.BCrypt;

public final class Passwords {
    private Passwords() {
    }

    public static String hash(String plain, int cost) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(cost));
    }

    public static boolean verify(String plain, String hash) {
        if (plain == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
