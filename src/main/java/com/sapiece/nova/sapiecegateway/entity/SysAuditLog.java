package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统审计日志实体类
 * 记录用户敏感操作，用于安全审计和问题追溯
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_audit_log")
public class SysAuditLog {

    // ==================== 基础信息 ====================

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 链路追踪ID
     * 用于关联同一请求的多条日志
     */
    @Column("trace_id")
    private String traceId;

    // ==================== 用户信息 ====================

    /**
     * 操作用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * 操作用户名
     */
    @Column("user_name")
    private String userName;

    /**
     * 用户昵称
     */
    @Column("nick_name")
    private String nickName;

    // ==================== 操作信息 ====================

    /**
     * 操作模块
     * AUTH-认证、USER-用户、ROLE-角色、ROUTE-路由、ADMIN-管理、GRAY-灰度、OAUTH-第三方登录
     */
    @Column("module")
    private String module;

    /**
     * 操作类型
     * LOGIN-登录、LOGOUT-登出、PASSWORD_CHANGE-修改密码、
     * CREATE-新增、UPDATE-修改、DELETE-删除、QUERY-查询等
     */
    @Column("operation")
    private String operation;

    /**
     * 操作描述
     */
    @Column("description")
    private String description;

    // ==================== 请求信息 ====================

    /**
     * 请求方法（GET、POST、PUT、DELETE等）
     */
    @Column("request_method")
    private String requestMethod;

    /**
     * 请求URL
     */
    @Column("request_url")
    private String requestUrl;

    /**
     * 请求参数（脱敏后）
     */
    @Column("request_params")
    private String requestParams;

    /**
     * 请求体（脱敏后）
     */
    @Column("request_body")
    private String requestBody;

    // ==================== 响应信息 ====================

    /**
     * 响应状态码
     */
    @Column("response_code")
    private Integer responseCode;

    /**
     * 响应消息
     */
    @Column("response_msg")
    private String responseMsg;

    /**
     * 响应数据
     */
    @Column("response_data")
    private String responseData;

    // ==================== 执行信息 ====================

    /**
     * 操作状态（0-失败 1-成功）
     */
    @Column("status")
    private Integer status;

    /**
     * 错误信息（操作失败时记录）
     */
    @Column("error_msg")
    private String errorMsg;

    /**
     * 执行耗时（毫秒）
     */
    @Column("cost_time")
    private Long costTime;

    // ==================== 客户端信息 ====================

    /**
     * 客户端IP地址
     */
    @Column("client_ip")
    private String clientIp;

    /**
     * 客户端地理位置
     */
    @Column("client_location")
    private String clientLocation;

    /**
     * 用户代理（浏览器信息）
     */
    @Column("user_agent")
    private String userAgent;

    /**
     * 设备类型（PC、Mobile、Tablet、Unknown）
     */
    @Column("device_type")
    private String deviceType;

    /**
     * 浏览器类型
     */
    @Column("browser")
    private String browser;

    /**
     * 操作系统
     */
    @Column("os")
    private String os;

    // ==================== OAuth2相关 ====================

    /**
     * OAuth2提供商（GITHUB、GOOGLE、WECHAT等）
     */
    @Column("oauth_provider")
    private String oauthProvider;

    /**
     * OAuth2用户ID
     */
    @Column("oauth_user_id")
    private String oauthUserId;

    // ==================== 时间信息 ====================

    /**
     * 操作时间
     */
    @Column("operate_time")
    private LocalDateTime operateTime;

    // ==================== 常量定义 ====================

    /**
     * 操作模块枚举
     */
    public static class Module {
        public static final String AUTH = "AUTH";       // 认证模块
        public static final String USER = "USER";       // 用户模块
        public static final String ROLE = "ROLE";       // 角色模块
        public static final String MENU = "MENU";       // 菜单模块
        public static final String ROUTE = "ROUTE";     // 路由模块
        public static final String ADMIN = "ADMIN";     // 管理模块
        public static final String GRAY = "GRAY";       // 灰度模块
        public static final String OAUTH = "OAUTH";     // OAuth模块
    }

    /**
     * 操作类型枚举
     */
    public static class Operation {
        public static final String LOGIN = "LOGIN";                     // 登录
        public static final String LOGOUT = "LOGOUT";                   // 登出
        public static final String LOGIN_FAILED = "LOGIN_FAILED";       // 登录失败
        public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE"; // 修改密码
        public static final String TOKEN_REFRESH = "TOKEN_REFRESH";     // Token刷新
        public static final String CREATE = "CREATE";                   // 新增
        public static final String UPDATE = "UPDATE";                   // 修改
        public static final String DELETE = "DELETE";                   // 删除
        public static final String QUERY = "QUERY";                     // 查询
        public static final String EXPORT = "EXPORT";                   // 导出
        public static final String IMPORT = "IMPORT";                   // 导入
        public static final String ENABLE = "ENABLE";                   // 启用
        public static final String DISABLE = "DISABLE";                 // 禁用
        public static final String OAUTH_LOGIN = "OAUTH_LOGIN";         // OAuth登录
        public static final String OAUTH_BIND = "OAUTH_BIND";           // OAuth绑定
        public static final String OAUTH_UNBIND = "OAUTH_UNBIND";       // OAuth解绑
    }

    /**
     * 操作状态枚举
     */
    public static class Status {
        public static final Integer FAIL = 0;     // 失败
        public static final Integer SUCCESS = 1;  // 成功
    }
}
