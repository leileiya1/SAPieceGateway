package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统角色实体类
 * 对应数据库表：t_sys_role
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_role")
public class SysRole {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 角色编码（如：ROLE_ADMIN、ROLE_USER）
     */
    @Column("role_code")
    private String roleCode;

    /**
     * 角色名称
     */
    @Column("role_name")
    private String roleName;

    /**
     * 显示顺序
     */
    @Column("role_sort")
    private Integer roleSort;

    /**
     * 状态 0-禁用 1-启用
     */
    @Column("status")
    private Integer status;

    /**
     * 删除标志 0-正常 1-删除
     */
    @Column("del_flag")
    private Integer delFlag;

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
     * 备注
     */
    @Column("remark")
    private String remark;
}
