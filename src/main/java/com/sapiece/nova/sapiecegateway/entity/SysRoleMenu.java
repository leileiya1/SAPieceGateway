package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 角色菜单关联实体类
 * 对应数据库表：t_sys_role_menu
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_role_menu")
public class SysRoleMenu {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 角色ID
     */
    @Column("role_id")
    private Long roleId;

    /**
     * 菜单ID
     */
    @Column("menu_id")
    private Long menuId;

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
}
