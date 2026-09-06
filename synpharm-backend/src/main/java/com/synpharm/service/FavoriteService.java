package com.synpharm.service;

import java.util.Map;

/**
 * 收藏服务接口
 */
public interface FavoriteService {

    void add(Long userId, Long resultId);

    void remove(Long userId, Long resultId);

    boolean isFavorited(Long userId, Long resultId);

    Map<String, Object> list(Long userId, Integer page, Integer pageSize);
}
