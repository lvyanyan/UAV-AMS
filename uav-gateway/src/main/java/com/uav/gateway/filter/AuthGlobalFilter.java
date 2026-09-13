package com.uav.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 网关全局鉴权过滤器：
 *   1. 白名单（uav.gateway.white-list）直接放行；
 *   2. 其余请求必须携带有效 Bearer token（jjwt 校验签名与有效期）；
 *   3. 校验通过后透传 X-User-Name / X-User-Role 请求头给下游服务（并剥离外部伪造的同名头）；
 *   4. 缺失/无效 token → 401 JSON，不走路由。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 尽早执行，先于路由转发 */
    public static final int FILTER_ORDER = -100;

    private static final String HEADER_AUTH = "Authorization";
    private static final String HEADER_USER = "X-User-Name";
    private static final String HEADER_ROLE = "X-User-Role";

    private final SecretKey key;
    private final List<String> whiteList;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public AuthGlobalFilter(@Value("${uav.jwt.secret}") String secret,
                            @Value("${uav.gateway.white-list}") String whiteListCsv) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.whiteList = Arrays.stream(whiteListCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 白名单放行（登录、公开字典、actuator）
        for (String pattern : whiteList) {
            if (matcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }

        String header = exchange.getRequest().getHeaders().getFirst(HEADER_AUTH);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        String token = header.substring(7);
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(HEADER_USER); // 防止外部伪造身份头
                    h.remove(HEADER_ROLE);
                })
                .header(HEADER_USER, username == null ? "" : username)
                .header(HEADER_ROLE, role == null ? "" : role)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 统一 401 JSON 响应（与 uav-common R 信封结构一致） */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"未登录或令牌无效\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
