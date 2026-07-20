package com.ventry.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "pfz/srQh1hqvsV8yHttL23iKS+WGfhd3cLJ54AvATNs=";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String validToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        Date now = new Date();
        return Jwts.builder()
                .subject("demo@ventry.com")
                .claim("userId", "1")
                .claim("role", "CUSTOMER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void allowsPublicAuthPathWithoutToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rejectsProtectedPathWithoutToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events"));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsProtectedPathWithGarbageToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events")
                        .header("Authorization", "Bearer not-a-real-token"));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsProtectedPathWithValidToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events")
                        .header("Authorization", "Bearer " + validToken()));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void forwardsVerifiedIdentityAsHeadersDownstream() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events")
                        .header("Authorization", "Bearer " + validToken()));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("1");
        assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("CUSTOMER");
    }

    @Test
    void overwritesClientSuppliedIdentityHeadersWithVerifiedValues() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/events")
                        .header("Authorization", "Bearer " + validToken())
                        .header("X-User-Id", "spoofed-id")
                        .header("X-User-Role", "ADMIN"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.get("X-User-Id")).containsExactly("1");
        assertThat(forwarded.get("X-User-Role")).containsExactly("CUSTOMER");
    }

    @Test
    void stripsClientSuppliedIdentityHeadersOnPublicAuthPath() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-User-Id", "spoofed-id")
                        .header("X-User-Role", "ADMIN"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        HttpHeaders forwarded = captor.getValue().getRequest().getHeaders();
        assertThat(forwarded.get("X-User-Id")).isNull();
        assertThat(forwarded.get("X-User-Role")).isNull();
    }
}
