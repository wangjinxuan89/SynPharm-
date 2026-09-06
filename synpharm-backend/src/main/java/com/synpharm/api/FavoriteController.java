package com.synpharm.api;

import com.synpharm.dto.request.FavoriteAddRequest;
import com.synpharm.service.FavoriteService;
import com.synpharm.utils.JwtUtils;
import com.synpharm.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 收藏控制器
 *
 * <p>处理预测结果收藏相关的HTTP请求，包括添加收藏、取消收藏、查询收藏状态与收藏列表。
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "收藏管理", description = "预测结果收藏接口")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final JwtUtils jwtUtils;

    /**
     * 添加收藏
     */
    @PostMapping
    @Operation(summary = "添加收藏", description = "收藏指定预测结果")
    public Result<Void> add(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody FavoriteAddRequest request) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        favoriteService.add(userId, request.getResultId());
        return Result.success();
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{resultId}")
    @Operation(summary = "取消收藏", description = "取消收藏指定预测结果")
    public Result<Void> remove(
            @RequestHeader("Authorization") String token,
            @PathVariable Long resultId) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        favoriteService.remove(userId, resultId);
        return Result.success();
    }

    /**
     * 查询收藏状态
     */
    @GetMapping("/{resultId}/status")
    @Operation(summary = "查询收藏状态", description = "查询指定结果是否已被收藏")
    public Result<Map<String, Boolean>> status(
            @RequestHeader("Authorization") String token,
            @PathVariable Long resultId) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(Map.of("favorited", favoriteService.isFavorited(userId, resultId)));
    }

    /**
     * 收藏列表
     */
    @GetMapping
    @Operation(summary = "获取收藏列表", description = "分页查询当前用户收藏的预测结果")
    public Result<Map<String, Object>> list(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = jwtUtils.getUserIdFromToken(token.replace("Bearer ", ""));
        return Result.success(favoriteService.list(userId, page, pageSize));
    }
}
