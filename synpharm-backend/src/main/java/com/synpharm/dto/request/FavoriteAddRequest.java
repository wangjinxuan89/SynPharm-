package com.synpharm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收藏请求DTO
 *
 * <p>收藏预测结果请求参数。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
public class FavoriteAddRequest {

    /** 结果ID */
    @NotNull(message = "结果ID不能为空")
    private Long resultId;
}
