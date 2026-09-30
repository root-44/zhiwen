package com.zhiwen.controller;

import com.zhiwen.common.ApiResponse;
import com.zhiwen.entity.Document;
import com.zhiwen.entity.DocumentChunk;
import com.zhiwen.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档接口:上传 / 列表 / 详情 / 切片查看
 */
@Validated
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /** 上传文档:multipart form,file 必填,title 可选 */
    @PostMapping("/upload")
    public ApiResponse<Document> upload(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.ok(documentService.upload(file, title), "文档处理完成");
    }

    @GetMapping
    public ApiResponse<List<Document>> list() {
        return ApiResponse.ok(documentService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Document> detail(@PathVariable Long id) {
        return ApiResponse.ok(documentService.getOrThrow(id));
    }

    /** 查看某文档的切片结果(调参时看切片质量用) */
    @GetMapping("/{id}/chunks")
    public ApiResponse<List<DocumentChunk>> chunks(@PathVariable Long id) {
        return ApiResponse.ok(documentService.chunksOf(id));
    }

    /** 手动重建向量:对 PENDING 切片补做 Embedding(遗留数据补录/换模型重建用) */
    @PostMapping("/{id}/reindex")
    public ApiResponse<Document> reindex(@PathVariable Long id) {
        return ApiResponse.ok(documentService.reindex(id), "向量重建完成");
    }
}
