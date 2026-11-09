package com.luyou.live.server.common;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final Algorithm algorithm;
    private final long ttlSeconds;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.ttl-seconds}") long ttlSeconds) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String userId) {
        return JWT.create()
                .withSubject(userId)
                .withIssuedAt(new Date())
                .withExpiresAt(Date.from(Instant.now().plusSeconds(ttlSeconds)))
                .sign(algorithm);
    }

    /** 校验并返回 userId；无效返回 null */
    public String verify(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            return jwt.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
