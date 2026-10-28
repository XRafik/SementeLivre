package com.sementelivre.backend.security;

import com.sementelivre.backend.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey chave;
    private final long expiration;

    /**
     * A chave vem de JWT_SECRET. Sem ela, só o perfil dev (que liga
     * jwt.chave-aleatoria-sem-secret) sobe, com uma chave sorteada na
     * subida: uma chave fixa no application-dev.yml ficava no git, e quem
     * lesse o repositório assinava token de qualquer conta.
     */
    public JwtService(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration:1800000}") long expiration,
            @Value("${jwt.chave-aleatoria-sem-secret:false}") boolean chaveAleatoriaSemSecret) {

        this.expiration = expiration;

        if (secret != null && !secret.isBlank()) {
            this.chave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!chaveAleatoriaSemSecret) {
            throw new IllegalStateException("Defina a variável JWT_SECRET (chave com 32 caracteres ou mais).");
        }
        byte[] aleatoria = new byte[64];
        new SecureRandom().nextBytes(aleatoria);
        this.chave = Keys.hmacShaKeyFor(aleatoria);
        log.warn("JWT_SECRET não definido: usando uma chave aleatória. Os logins caem quando o backend reinicia.");
    }

    private SecretKey getSigningKey() {
        return chave;
    }

    public String gerarToken(Usuario usuario) {
        String rolesStr = usuario.getRoles().stream()
                .map(r -> r.getNome().name())
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("id", usuario.getId().toString())
                .claim("nome", usuario.getNome())
                .claim("roles", rolesStr)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    public boolean tokenValido(String token) {
        try {
            Claims claims = extrairClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
