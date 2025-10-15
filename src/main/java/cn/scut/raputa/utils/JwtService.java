package cn.scut.raputa.utils;

import cn.scut.raputa.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final Key key;
    private final long expMinutes;

    public JwtService(@Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.exp-minutes:720}") long expMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expMinutes = expMinutes;
    }

    public String generateToken(User u) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("uid", u.getId())
                .claim("dep", u.getDepartmentName())
                .claim("hos", u.getHospitalName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(expMinutes))))
                .signWith(key)
                .compact();
    }
}