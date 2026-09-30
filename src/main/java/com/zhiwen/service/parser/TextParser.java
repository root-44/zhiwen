package com.zhiwen.service.parser;

import com.zhiwen.common.BusinessException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 纯文本解析器:Markdown / TXT 通吃
 * Markdown 先去掉 # 标记、图片链接等语法噪声,只留正文,避免噪声稀释向量相关性
 */
@Component
public class TextParser implements DocParser {

    @Override
    public List<String> supportedTypes() {
        return List.of("md", "txt", "markdown");
    }

    @Override
    public List<ParsedSection> parse(String fileName, InputStream in) {
        try {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                throw fail("文件内容为空");
            }
            String text = "md".equals(extensionOf(fileName)) || "markdown".equals(extensionOf(fileName))
                    ? stripMarkdown(raw) : raw;
            return List.of(new ParsedSection(text.trim(), null));
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw fail("读取文件失败: " + e.getMessage());
        }
    }

    /** 去除 Markdown 语法噪声:标题符号、图片、链接包裹、代码围栏、强调符号 */
    private String stripMarkdown(String raw) {
        return raw
                .replaceAll("```[\\s\\S]*?```", " ")                 // 代码块整体去掉(代码对问答价值低)
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")        // 图片
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")      // 链接只留文字
                .replaceAll("(?m)^#{1,6}\\s*", "")                   // 标题井号
                .replaceAll("(?m)^>\\s?", "")                        // 引用符号
                .replaceAll("[*_`~]{1,3}", "")                       // 强调符号
                .replaceAll("\\n{3,}", "\n\n");                      // 压缩多余空行
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
