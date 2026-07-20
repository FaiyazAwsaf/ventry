package com.ventry.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

/**
 * Verifies JWTs locally using the same secret Auth Service signs with, rather than
 * calling Auth Service on every request — that's the whole point of stateless JWT auth.
 * Also forwards the verified caller identity downstream as X-User-Id/X-User-Role headers,
 * since this is the only point in the system that ever sees the raw JWT.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String PUBLIC_PATH_PREFIX = "/api/auth/";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(PUBLIC_PATH_PREFIX)) {
            return chain.filter(stripIdentityHeaders(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange);
        }

        String token = authHeader.substring("Bearer ".length());
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange);
        }

        return chain.filter(forwardIdentityHeaders(exchange, claims));
    }

    /**
     * Client-supplied X-User-Id/X-User-Role headers must never reach a downstream service
     * verbatim — only values this filter derives from a verified JWT are trustworthy.
     */
    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    private ServerWebExchange forwardIdentityHeaders(ServerWebExchange exchange, Claims claims) {
        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, userId);
                    headers.set(USER_ROLE_HEADER, role);
                })
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
