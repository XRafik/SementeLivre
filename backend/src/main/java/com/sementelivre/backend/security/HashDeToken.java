package com.sementelivre.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * O banco guarda só o SHA-256 do refresh token e do token de recuperação de
 * senha (#206): quem lê o banco (um backup, outra falha) não tem o token para
 * usar. Os tokens são aleatórios, então não precisam de sal nem de BCrypt.
 */
public final class HashDeToken {

    private HashDeToken() {
    }

    /** Hex minúsculo; igual ao encode(sha256(convert_to(token, 'UTF8')), 'hex') da migração V12. */
    public static String sha256(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 sempre existe na JVM", e);
        }
    }
}
