package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 系统菜单权限实体类
 * 对应数据库表：t_sys_menu
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_menu")
public class SysMenu {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 父菜单ID
     */
    @Column("parent_id")
    private Long parentId;

    /**
     * 菜单名称
     */
    @Column("menu_name")
    private String menuName;

    /**
     * 菜单类型 M-目录 C-菜单 F-按钮
     */
    @Column("menu_type")
    private String menuType;

    /**
     * 显示顺序
     */
    @Column("menu_sort")
    private Integer menuSort;

    /**
     * 权限标识（如：system:user:list）
     */
    @Column("permission_code")
    private String permissionCode;

    /**
     * 路由地址
     */
    @Column("path")
    private String path;

    /**
     * 组件路径
     */
    @Column("component")
    private String component;

    /**
     * 菜单图标
     */
    @Column("icon")
    private String icon;

    /**
     * 网关目标URI（如：http://localhost:8081 或 lb://user-service）
     */
    @Column("target_uri")
    private String targetUri;

    /**
     * 网关路由断言配置（JSON格式）
     */
    @Column("route_predicates")
    private String routePredicates;

    /**
     * 网关路由过滤器配置（JSON格式）
     */
    @Column("route_filters")
    private String routeFilters;

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
