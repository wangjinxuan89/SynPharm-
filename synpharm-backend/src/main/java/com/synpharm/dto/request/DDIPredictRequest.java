package com.synpharm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DDI预测请求DTO
 * 
 * <p>药物-药物相互作用预测请求参数。
 * 
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
public class DDIPredictRequest {

    /** 药物A标识（DrugBank ID，须在 DDI-LLM 训练图内） */
    @NotBlank(message = "药物A不能为空")
    private String drugA;

    /** 药物B标识（DrugBank ID，须在 DDI-LLM 训练图内） */
    @NotBlank(message = "药物B不能为空")
    private String drugB;
}