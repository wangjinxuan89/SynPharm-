package com.synpharm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synpharm.dto.request.DDIPredictRequest;
import com.synpharm.dto.request.DTIPredictRequest;
import com.synpharm.dto.request.GeneralPredictRequest;
import com.synpharm.dto.request.PPIPredictRequest;
import com.synpharm.dto.response.PredictResultResponse;
import com.synpharm.model.entity.PredictResult;
import com.synpharm.model.entity.PredictTask;
import com.synpharm.pipeline.PipelineFactory;
import com.synpharm.repository.mapper.PredictResultMapper;
import com.synpharm.repository.mapper.PredictTaskMapper;
import com.synpharm.service.PredictService;
import com.synpharm.service.ResultResponseAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictServiceImpl implements PredictService {

    private final PipelineFactory pipelineFactory;
    private final PredictTaskMapper taskMapper;
    private final PredictResultMapper resultMapper;
    private final ObjectMapper objectMapper;
    private final ResultResponseAssembler assembler;

    @Override
    @Deprecated
    public PredictResultResponse predictDTI(DTIPredictRequest request, Long userId) {
        log.warn("已废弃的方法 predictDTI，请使用通用预测接口 predict()");
        return predict(GeneralPredictRequest.builder()
                .inputType("smiles")
                .algoType("DTI")
                .outputType("json")
                .inputValue(request.getSmiles() + "," + request.getTargetSeq())
                .build(), userId);
    }

    @Override
    @Deprecated
    public PredictResultResponse predictPPI(PPIPredictRequest request, Long userId) {
        log.warn("已废弃的方法 predictPPI，请使用通用预测接口 predict()");
        return predict(GeneralPredictRequest.builder()
                .inputType("smiles")
                .algoType("PPI")
                .outputType("json")
                .inputValue(request.getProteinA() + "," + request.getProteinB())
                .build(), userId);
    }

    @Override
    @Deprecated
    public PredictResultResponse predictDDI(DDIPredictRequest request, Long userId) {
        log.warn("已废弃的方法 predictDDI，请使用通用预测接口 predict()");
        return predict(GeneralPredictRequest.builder()
                .inputType("smiles")
                .algoType("DDI")
                .outputType("json")
                .inputValue(request.getDrugA() + "," + request.getDrugB())
                .build(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PredictResultResponse predict(GeneralPredictRequest request, Long userId) {
        log.info("通用预测请求: userId={}, inputType={}, algoType={}, outputType={}",
                userId, request.getInputType(), request.getAlgoType(), request.getOutputType());

        PredictResultResponse response = pipelineFactory.process(
                request.getInputType(),
                request.getAlgoType(),
                request.getOutputType(),
                request.getInputValue(),
                request.getFileUrl()
        );

        // 预测结果落库：创建隐式任务 + 结果记录（供历史/结果查询使用）
        savePrediction(request, userId, response);

        return response;
    }

    @Override
    public List<PredictResultResponse> getHistory(Long userId) {
        List<PredictResult> entities = resultMapper.selectList(
                new LambdaQueryWrapper<PredictResult>()
                        .eq(PredictResult::getUserId, userId)
                        .orderByDesc(PredictResult::getId)
        );
        List<PredictResultResponse> list = new ArrayList<>();
        for (PredictResult entity : entities) {
            list.add(assembler.toResponse(entity));
        }
        return list;
    }

    /**
     * 落库：为单条预测创建隐式任务记录 + 结果记录。
     * <p>predict_result.task_id 为 NOT NULL 外键，故先创建任务再创建结果。
     */
    private void savePrediction(GeneralPredictRequest request, Long userId, PredictResultResponse response) {
        try {
            // 1. 创建隐式任务
            PredictTask task = new PredictTask();
            task.setTaskNo(generateNo("T"));
            task.setUserId(userId);
            task.setPredictType(toPredictType(response.getAlgoType() != null ? response.getAlgoType() : request.getAlgoType()));
            task.setInputType(request.getInputType());
            task.setInputValue(request.getInputValue());
            task.setFileUrl(request.getFileUrl());
            task.setStatus("completed");
            task.setProgress(100);
            task.setStartedAt(LocalDateTime.now());
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.insert(task);

            // 2. 创建预测结果
            PredictResult entity = new PredictResult();
            entity.setResultNo(generateNo("R"));
            entity.setTaskId(task.getId());
            entity.setUserId(userId);
            entity.setTargetId(response.getTargetId());
            entity.setTargetName(response.getTargetName());
            entity.setLigandSmiles(extractLigandSmiles(request, response.getAlgoType()));
            entity.setBindingAffinity(response.getBindingAffinity());
            entity.setConfidenceScore(response.getConfidenceScore());
            entity.setConfidenceLevel(response.getConfidenceLevel());
            entity.setInteractions(writeJson(response.getInteractions()));
            entity.setPredictionData(writeJson(response));
            entity.setDatasetSource("single-predict");
            resultMapper.insert(entity);

            response.setId(entity.getId());
            response.setCreatedAt(entity.getCreatedAt());
            response.setLigandSmiles(entity.getLigandSmiles());
            log.info("预测结果落库成功: resultId={}, taskId={}", entity.getId(), task.getId());
        } catch (Exception e) {
            // 落库失败不应阻断预测结果返回
            log.error("预测结果落库失败: {}", e.getMessage());
        }
    }

    private String toPredictType(String algoType) {
        return algoType == null ? null : algoType.toLowerCase();
    }

    private String extractLigandSmiles(GeneralPredictRequest request, String algoType) {
        if (algoType != null && ("DTI".equalsIgnoreCase(algoType) || "DDI".equalsIgnoreCase(algoType))) {
            String value = request.getInputValue();
            if (value != null && value.contains(",")) {
                return value.split(",")[0].trim();
            }
            return value;
        }
        return null;
    }

    private String generateNo(String prefix) {
        return prefix + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String writeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

}