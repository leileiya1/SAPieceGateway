package com.sapiece.nova.sapiecegateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成、解析和验证JWT令牌
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT密钥（从配置文件读取）
     */
    @Value("${jwt.secret:SAPiece-Gateway-Secret-Key-2025-For-Authentication-And-Authorization-Security}")
    private String secret;

    /**
     * JWT过期时间（毫秒）默认7天
     */
    @Value("${jwt.expiration:604800000}")
    private Long expiration;

    /**
     * JWT刷新时间（毫秒）默认3天
     */
    @Value("${jwt.refresh:259200000}")
    private Long refreshTime;

    /**
     * 生成SecretKey
     *
     * @return SecretKey
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token
     *
     * @param userId      用户ID
     * @param userName    用户名
     * @param roles       角色列表
     * @param permissions 权限列表
     * @return JWT Token
     */
    public String generateToken(Long userId, String userName, List<String> roles, List<String> permissions) {
        log.debug("生成JWT Token, userId: {}, userName: {}", userId, userName);
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + expiration);

            String token = Jwts.builder()
                    .subject(userName) // 主题：用户名
                    .claim("userId", userId) // 自定义声明：用户ID
                    .claim("userName", userName) // 自定义声明：用户名
                    .claim("roles", roles) // 自定义声明：角色列表
                    .claim("permissions", permissions) // 自定义声明：权限列表
                    .issuedAt(now) // 签发时间
                    .expiration(expiryDate) // 过期时间
                    .signWith(getSecretKey()) // 签名
                    .compact();

            log.info("成功生成JWT Token, userId: {}, userName: {}, expiryDate: {}", userId, userName, expiryDate);
            return token;
        } catch (Exception e) {
            log.error("生成JWT Token失败, userId: {}, userName: {}, error: {}", userId, userName, e.getMessage());
            throw new RuntimeException("生成JWT Token失败", e);
        }
    }

    /**
     * 从Token中解析Claims
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseToken(String token) {
        log.debug("解析JWT Token");
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            log.debug("成功解析JWT Token, subject: {}", claims.getSubject());
            return claims;
        } catch (Exception e) {
            log.error("解析JWT Token失败, error: {}", e.getMessage());
            throw new RuntimeException("解析JWT Token失败", e);
        }
    }

    /**
     * 从Token中获取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return (Long) userId;
    }

    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUserNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userName", String.class);
    }

    /**
     * 从Token中获取角色列表
     *
     * @param token JWT Token
     * @return 角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("roles", List.class);
    }

    /**
     * 从Token中获取权限列表
     *
     * @param token JWT Token
     * @return 权限列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("permissions", List.class);
    }

    /**
     * 从Token中获取签发时间
     *
     * @param token JWT Token
     * @return 签发时间
     */
    public Date getIssuedAtFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getIssuedAt();
    }

    /**
     * 验证Token是否有效
     *
     * @param token JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        log.debug("验证JWT Token");
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            boolean isValid = expiration.after(new Date());
            if (isValid) {
                log.debug("JWT Token验证成功");
            } else {
                log.warn("JWT Token已过期, expiration: {}", expiration);
            }
            return isValid;
        } catch (Exception e) {
            log.error("JWT Token验证失败, error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证Token是否在密码修改之前签发（密码修改后旧Token应失效）
     *
     * @param token JWT Token
     * @param passwordLastChangedAt 密码最后修改时间
     * @return true-Token在密码修改前签发（应失效），false-Token有效
     */
    public boolean isTokenIssuedBeforePasswordChange(String token, LocalDateTime passwordLastChangedAt) {
        // 如果密码修改时间为空，说明从未修改过密码，Token有效
        if (passwordLastChangedAt == null) {
            log.debug("密码从未修改过，Token有效");
            return false;
        }

        try {
            // 获取Token签发时间
            Date issuedAt = getIssuedAtFromToken(token);

            // 将LocalDateTime转换为Date进行比较
            Date passwordChangeDate = Date.from(passwordLastChangedAt.atZone(ZoneId.systemDefault()).toInstant());

            // 如果Token签发时间早于密码修改时间，则Token应失效
            boolean isInvalid = issuedAt.before(passwordChangeDate);

            if (isInvalid) {
                log.warn("Token在密码修改前签发，已失效. tokenIssuedAt: {}, passwordChangedAt: {}",
                    issuedAt, passwordLastChangedAt);
            } else {
                log.debug("Token在密码修改后签发，有效");
            }

            return isInvalid;
        } catch (Exception e) {
            log.error("验证Token签发时间失败, error: {}", e.getMessage());
            // 出错时为了安全起见，认为Token无效
            return true;
        }
    }

    /**
     * 判断Token是否需要刷新
     *
     * @param token JWT Token
     * @return 是否需要刷新
     */
    public boolean shouldRefresh(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            Date now = new Date();
            long timeLeft = expiration.getTime() - now.getTime();
            boolean shouldRefresh = timeLeft < refreshTime;
            if (shouldRefresh) {
                log.info("JWT Token需要刷新, timeLeft: {}ms", timeLeft);
            }
            return shouldRefresh;
        } catch (Exception e) {
            log.error("判断Token是否需要刷新失败, error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 刷新Token
     *
     * @param token 旧Token
     * @return 新Token
     */
    public String refreshToken(String token) {
        log.debug("刷新JWT Token");
        try {
            Claims claims = parseToken(token);
            Long userId = getUserIdFromToken(token);
            String userName = getUserNameFromToken(token);
            List<String> roles = getRolesFromToken(token);
            List<String> permissions = getPermissionsFromToken(token);

            String newToken = generateToken(userId, userName, roles, permissions);
            log.info("成功刷新JWT Token, userId: {}, userName: {}", userId, userName);
            return newToken;
        } catch (Exception e) {
            log.error("刷新JWT Token失败, error: {}", e.getMessage());
            throw new RuntimeException("刷新JWT Token失败", e);
        }
    }

    /**
     * 从Token中提取所有信息
     *
     * @param token JWT Token
     * @return Token信息Map
     */
    public Map<String, Object> getTokenInfo(String token) {
        Claims claims = parseToken(token);
        return Map.of(
                "userId", claims.get("userId"),
                "userName", claims.get("userName"),
                "roles", claims.get("roles"),
                "permissions", claims.get("permissions"),
                "issuedAt", claims.getIssuedAt(),
                "expiration", claims.getExpiration()
        );
    }
}
