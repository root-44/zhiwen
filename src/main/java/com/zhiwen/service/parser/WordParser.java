package com.zhiwen.service.parser;

import com.zhiwen.common.BusinessException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Word 解析器:POI 同时支持 docx(2007+/XWPF)与 doc(97-2003/HWPF)
 * Word 没有可靠页码概念(分页由渲染决定),整篇作为一个文本段
 */
@Component
public class WordParser implements DocParser {

    @Override
    public List<String> supportedTypes() {
        return List.of("docx", "doc");
    }

    @Override
    public List<ParsedSection> parse(String fileName, InputStream in) {
        String ext = extensionOf(fileName);
        try {
            String text = "docx".equals(ext) ? parseDocx(in) : parseDoc(in);
            if (text == null || text.isBlank()) {
                throw fail("Word 文档未提取到文本");
            }
            return List.of(new ParsedSection(text.trim(), null));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw fail("Word 解析异常: " + e.getMessage());
        }
    }

    private String parseDocx(InputStream in) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String parseDoc(InputStream in) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
