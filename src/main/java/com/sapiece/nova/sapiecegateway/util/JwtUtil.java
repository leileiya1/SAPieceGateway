package com.sapiece.nova.sapiecegateway.util;

import cn.hutool.core.util.IdUtil;
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
    @Value("${jwt.secret}")
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
     * Access Token过期时间（毫秒）默认30分钟
     */
    @Value("${jwt.access-token-expiration:1800000}")
    private Long accessTokenExpiration;

    /**
     * Refresh Token过期时间（毫秒）默认7天
     */
    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpiration;

    /**
     * Token类型常量
     */
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    /**
     * 生成SecretKey
     *
     * @return SecretKey
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token（兼容旧方法，默认生成Access Token）
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @return JWT Token
     */
    public String generateToken(Long userId, String userName) {
        return generateAccessToken(userId, userName);
    }

    /**
     * 生成Access Token（短期有效，用于API访问）
     * 包含pwdVer（密码版本号），用于无DB校验Token是否在密码修改前签发
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @param pwdVer   密码版本号（passwordLastChangedAt epoch second，未改过密码传0）
     * @return Access Token
     */
    public String generateAccessToken(Long userId, String userName, long pwdVer) {
        log.debug("生成Access Token, userId: {}, userName: {}", userId, userName);
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

            String token = Jwts.builder()
                    .id(IdUtil.simpleUUID())
                    .subject(userName)
                    .claim("userId", userId)
                    .claim("pwdVer", pwdVer)
                    .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                    .issuedAt(now)
                    .expiration(expiryDate)
                    .signWith(getSecretKey())
                    .compact();

            log.info("成功生成Access Token, userId: {}, userName: {}, expiryDate: {}", userId, userName, expiryDate);
            return token;
        } catch (Exception e) {
            log.error("生成Access Token失败, userId: {}, userName: {}, error: {}", userId, userName, e.getMessage());
            throw new RuntimeException("生成Access Token失败", e);
        }
    }

    /** 兼容旧调用，pwdVer默认0 */
    public String generateAccessToken(Long userId, String userName) {
        return generateAccessToken(userId, userName, 0L);
    }

    /**
     * 生成Refresh Token（长期有效，用于刷新Access Token）
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @return Refresh Token
     */
    public String generateRefreshToken(Long userId, String userName) {
        log.debug("生成Refresh Token, userId: {}, userName: {}", userId, userName);
        try {
            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

            String token = Jwts.builder()
                    .id(IdUtil.simpleUUID())
                    .subject(userName)
                    .claim("userId", userId)
                    .claim("userName", userName)
                    .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                    .issuedAt(now)
                    .expiration(expiryDate)
                    .signWith(getSecretKey())
                    .compact();

            log.info("成功生成Refresh Token, userId: {}, userName: {}, expiryDate: {}", userId, userName, expiryDate);
            return token;
        } catch (Exception e) {
            log.error("生成Refresh Token失败, userId: {}, userName: {}, error: {}", userId, userName, e.getMessage());
            throw new RuntimeException("生成Refresh Token失败", e);
        }
    }

    /**
     * 生成双Token（Access Token + Refresh Token）
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @param pwdVer   密码版本号
     */
    public Map<String, String> generateTokenPair(Long userId, String userName, long pwdVer) {
        log.debug("生成双Token, userId: {}, userName: {}", userId, userName);
        String accessToken = generateAccessToken(userId, userName, pwdVer);
        String refreshToken = generateRefreshToken(userId, userName);
        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }

    /** 兼容旧调用 */
    public Map<String, String> generateTokenPair(Long userId, String userName) {
        return generateTokenPair(userId, userName, 0L);
    }

    /**
     * 从Token中提取pwdVer，Token未包含该字段时返回0
     */
    public long getPwdVerFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            Object pwdVer = claims.get("pwdVer");
            if (pwdVer instanceof Number n) return n.longValue();
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 获取Token类型
     *
     * @param token JWT Token
     * @return Token类型（access/refresh）
     */
    public String getTokenType(String token) {
        try {
            Claims claims = parseToken(token);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            // 兼容旧Token（没有tokenType字段的视为access token）
            return tokenType != null ? tokenType : TOKEN_TYPE_ACCESS;
        } catch (Exception e) {
            log.debug("获取Token类型失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为Access Token
     *
     * @param token JWT Token
     * @return 是否为Access Token
     */
    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }

    /**
     * 判断是否为Refresh Token
     *
     * @param token JWT Token
     * @return 是否为Refresh Token
     */
    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
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
     * 精简版JWT从subject获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUserNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
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
     * 刷新Token（精简版）
     *
     * @param token 旧Token
     * @return 新Token
     */
    public String refreshToken(String token) {
        log.debug("刷新JWT Token");
        try {
            Long userId = getUserIdFromToken(token);
            String userName = getUserNameFromToken(token);

            String newToken = generateToken(userId, userName);
            log.info("成功刷新JWT Token, userId: {}, userName: {}", userId, userName);
            return newToken;
        } catch (Exception e) {
            log.error("刷新JWT Token失败, error: {}", e.getMessage());
            throw new RuntimeException("刷新JWT Token失败", e);
        }
    }

    /**
     * 从Token中提取基本信息（精简版）
     * 不再包含roles和permissions，这些信息从Redis获取
     *
     * @param token JWT Token
     * @return Token信息Map
     */
    public Map<String, Object> getTokenInfo(String token) {
        Claims claims = parseToken(token);
        return Map.of(
                "userId", claims.get("userId"),
                "userName", claims.getSubject(),
                "tokenType", getTokenType(token),
                "issuedAt", claims.getIssuedAt(),
                "expiration", claims.getExpiration()
        );
    }
}
