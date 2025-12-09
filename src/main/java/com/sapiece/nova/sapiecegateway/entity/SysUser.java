package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库表：t_sys_user
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_user")
public class SysUser {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 用户名
     */
    @Column("user_name")
    private String userName;

    /**
     * 用户昵称
     */
    @Column("nick_name")
    private String nickName;

    /**
     * 密码（加密后）
     */
    @Column("password")
    private String password;

    /**
     * 邮箱
     */
    @Column("email")
    private String email;

    /**
     * 手机号
     */
    @Column("mobile")
    private String mobile;

    /**
     * 头像地址
     */
    @Column("avatar")
    private String avatar;

    /**
     * 性别 0-未知 1-男 2-女
     */
    @Column("gender")
    private Integer gender;

    /**
     * 状态 0-禁用 1-启用
     */
    @Column("status")
    private Integer status;

    /**
     * 账号是否为私密 0-否 1-是
     */
    @Column("private_flag")
    private Integer privateFlag;

    /**
     * 删除标志 0-正常 1-删除
     */
    @Column("del_flag")
    private Integer delFlag;

    /**
     * 最后登录IP
     */
    @Column("login_ip")
    private String loginIp;

    /**
     * 最后登录时间
     */
    @Column("login_time")
    private LocalDateTime loginTime;

    /**
     * 创建人
     */
    @Column("creator")
    private String creator;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    @Column("updater")
    private String updater;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 用户名修改时间
     */
    @Column("user_name_last_changed_at")
    private LocalDateTime userNameLastChangedAt;

    /**
     * 昵称修改时间
     */
    @Column("nick_name_last_changed_at")
    private LocalDateTime nickNameLastChangedAt;

    /**
     * 密码修改时间
     */
    @Column("password_last_changed_at")
    private LocalDateTime passwordLastChangedAt;

    /**
     * 请求删除时间
     */
    @Column("deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    /**
     * 计划永久删除时间
     */
    @Column("scheduled_permanent_deletion_at")
    private LocalDateTime scheduledPermanentDeletionAt;

    /**
     * 上次活跃时间
     */
    @Column("last_active_at")
    private LocalDateTime lastActiveAt;

    /**
     * 备注
     */
    @Column("remark")
    private String remark;
}
