package com.zhiwen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 问答请求(非流式 POST) */
@Data
public class ChatQuestion {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过 500 字")
    private String question;
}
