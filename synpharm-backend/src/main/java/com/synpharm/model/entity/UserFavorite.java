package com.synpharm.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收藏实体
 *
 * <p>映射数据库表 user_favorite，存储用户对预测结果的收藏。
 * 字段与建表脚本 05_user_favorite.sql 保持一致。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
@TableName("user_favorite")
public class UserFavorite {

    /** 收藏ID（主键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 结果ID（外键 -> predict_result.id） */
    private Long resultId;

    /** 备注 */
    private String note;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 删除标记（0未删除，1已删除） */
    @TableLogic
    private Integer deleted;
}
