package com.synpharm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTI预测请求DTO
 * 
 * <p>药物-靶点相互作用预测请求参数。
 * 
 * @author SynPharm Team
 * @version 1.0.0
 */
@Data
public class DTIPredictRequest {

    /** 药物分子的SMILES表达式 */
    @NotBlank(message = "SMILES不能为空")
    private String smiles;

    /** 靶点蛋白氨基酸序列（KAN-MoDTI 需要 ≥31 残基的序列，非 UniProt/PDB ID） */
    @NotBlank(message = "靶点序列不能为空")
    private String targetSeq;
}