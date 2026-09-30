package com.zhiwen.controller;

import com.zhiwen.common.ApiResponse;
import com.zhiwen.dto.SearchHit;
import com.zhiwen.service.search.RagSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 语义检索接口:D3 验收核心
 * 示例:GET /api/v1/search?q=水稻穗头发病减产严重怎么办
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final RagSearchService ragSearchService;

    @GetMapping
    public ApiResponse<List<SearchHit>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "topK", required = false) Integer topK,
            @RequestParam(value = "threshold", required = false) Double threshold) {
        if (query == null || query.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        String q = query.trim();
        // 默认参数(问答链路同款)走 Redis 检索缓存;显式传 topK/threshold 视为调参,绕过缓存避免结果串用
        if (topK == null && threshold == null) {
            return ApiResponse.ok(ragSearchService.search(q));
        }
        return ApiResponse.ok(ragSearchService.search(q, topK, threshold));
    }
}
