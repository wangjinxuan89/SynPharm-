package com.synpharm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.dto.response.PredictResultResponse;
import com.synpharm.model.entity.PredictResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 预测结果响应装配器
 *
 * <p>把 predict_result 实体转成前端契约 {@link PredictResultResponse}。
 * 抽出来供 {@code ResultServiceImpl}、{@code PredictServiceImpl} 与收藏列表复用，
 * 避免三处重复反序列化 interactions / 提取 algoType 的逻辑。
 *
 * @author SynPharm Team
 * @version 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultResponseAssembler {

    private final ObjectMapper objectMapper;

    public PredictResultResponse toResponse(PredictResult entity) {
        String algoType = null;
        if (entity.getPredictionData() != null) {
            try {
                JsonNode node = objectMapper.readTree(entity.getPredictionData());
                if (node.has("algoType") && !node.get("algoType").isNull()) {
                    algoType = node.get("algoType").asText();
                }
            } catch (Exception ignored) {
                // 提取失败则 algoType 为 null
            }
        }

        return PredictResultResponse.builder()
                .id(entity.getId())
                .algoType(algoType)
                .targetId(entity.getTargetId())
                .targetName(entity.getTargetName())
                .ligandSmiles(entity.getLigandSmiles())
                .bindingAffinity(entity.getBindingAffinity())
                .confidenceScore(entity.getConfidenceScore())
                .confidenceLevel(entity.getConfidenceLevel())
                .interactions(parseInteractions(entity.getInteractions()))
                .createdAt(entity.getCreatedAt())
                .datasetInfo(defaultDatasetInfo(algoType))
                .build();
    }

    private PredictResultResponse.DatasetInfo defaultDatasetInfo(String algoType) {
        return PredictResultResponse.DatasetInfo.builder()
                .name(algoType == null ? "AI预测" : algoType + "预测结果")
                .size(0)
                .description("由 FastAPI 算法引擎计算")
                .source("fastapi")
                .build();
    }

    private List<PredictResultResponse.InteractionInfo> parseInteractions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PredictResultResponse.InteractionInfo>>() {
            });
        } catch (Exception e) {
            log.warn("相互作用JSON解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
