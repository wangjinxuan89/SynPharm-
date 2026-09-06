package com.synpharm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.synpharm.dto.response.PredictResultResponse;
import com.synpharm.exception.BusinessException;
import com.synpharm.exception.ErrorCode;
import com.synpharm.model.entity.PredictResult;
import com.synpharm.model.entity.UserFavorite;
import com.synpharm.repository.mapper.PredictResultMapper;
import com.synpharm.repository.mapper.UserFavoriteMapper;
import com.synpharm.service.FavoriteService;
import com.synpharm.service.ResultResponseAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏服务实现
 *
 * <p>基于 user_favorite 表实现收藏的增删查，并按 result_id 关联
 * predict_result 返回收藏的结果列表。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final UserFavoriteMapper favoriteMapper;
    private final PredictResultMapper resultMapper;
    private final ResultResponseAssembler assembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Long resultId) {
        PredictResult result = resultMapper.selectById(resultId);
        if (result == null) {
            throw new BusinessException(ErrorCode.RESULT_NOT_FOUND);
        }

        // 唯一键 uk_user_result 幂等：已收藏则直接返回
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getResultId, resultId)
        );
        if (count != null && count > 0) {
            return;
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setResultId(resultId);
        favoriteMapper.insert(favorite);
        log.info("收藏成功: userId={}, resultId={}", userId, resultId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long resultId) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getResultId, resultId)
        );
        log.info("取消收藏: userId={}, resultId={}", userId, resultId);
    }

    @Override
    public boolean isFavorited(Long userId, Long resultId) {
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getResultId, resultId)
        );
        return count != null && count > 0;
    }

    @Override
    public Map<String, Object> list(Long userId, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;

        // 查询当前用户全部收藏（按收藏时间倒序），内存分页（项目未配置分页插件）
        List<UserFavorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getId)
        );

        int total = favorites.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(total, from + safeSize);
        List<UserFavorite> pageFavorites = from < total ? favorites.subList(from, to) : List.of();

        List<PredictResultResponse> list = new ArrayList<>();
        if (!pageFavorites.isEmpty()) {
            List<Long> resultIds = pageFavorites.stream()
                    .map(UserFavorite::getResultId)
                    .distinct()
                    .toList();
            List<PredictResult> results = resultMapper.selectBatchIds(resultIds);

            Map<Long, PredictResult> resultMap = new HashMap<>();
            for (PredictResult r : results) {
                resultMap.put(r.getId(), r);
            }
            // 按收藏顺序组装响应（跳过已被逻辑删除的结果）
            for (UserFavorite favorite : pageFavorites) {
                PredictResult r = resultMap.get(favorite.getResultId());
                if (r != null) {
                    list.add(assembler.toResponse(r));
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }
}
