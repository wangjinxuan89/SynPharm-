package com.synpharm.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.model.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收藏数据访问接口
 *
 * <p>继承MyBatisPlus的BaseMapper，提供用户收藏数据的CRUD操作。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
