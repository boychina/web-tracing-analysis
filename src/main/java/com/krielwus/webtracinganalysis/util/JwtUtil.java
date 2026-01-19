package com.krielwus.webtracinganalysis.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class JwtUtil {
    @Value("${JWT_SECRET:}")
    private String jwtSecret;
    @Value("${ACCESS_TOKEN_TTL_MINUTES:15}")
    private long accessTtlMinutes;
    @Value("${TOKEN_ISSUER:web-tracing-analysis}")
    private String issuer;

    private volatile Algorithm algorithm;
    private String runtimeSecret;

    private Algorithm alg() {
        if (algorithm == null) {
            String secret = jwtSecret;
            if (secret == null || secret.trim().isEmpty()) {
                byte[] buf = new byte[32];
                new SecureRandom().nextBytes(buf);
                secret = Base64.getEncoder().encodeToString(buf);
                runtimeSecret = secret;
            }
            algorithm = Algorithm.HMAC256(secret.getBytes());
        }
        return algorithm;
    }

    public String createAccessToken(Long userId, String username, String role, Map<String, String> extra) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTtlMinutes * 60);
        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(exp))
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withClaim("role", role);
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                builder.withClaim(e.getKey(), e.getValue());
            }
        }
        return builder.sign(alg());
    }

    public DecodedJWT verify(String token) {
        JWTVerifier verifier = JWT.require(alg()).withIssuer(issuer).build();
        return verifier.verify(token);
    }
}
