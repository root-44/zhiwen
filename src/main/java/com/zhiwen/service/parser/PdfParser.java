package com.zhiwen.service.parser;

import com.zhiwen.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 解析器:PDFBox 3.x,按页提取文本保留页码
 *
 * <p>为什么逐页提取而不是整篇:1) 页码是引用溯源的关键信息;
 * 2) 大文件整篇读入内存容易 OOM,逐页处理内存占用平稳。
 */
@Component
public class PdfParser implements DocParser {

    @Override
    public List<String> supportedTypes() {
        return List.of("pdf");
    }

    @Override
    public List<ParsedSection> parse(String fileName, InputStream in) {
        // PDFBox 3 的 Loader 需要 File,先落到临时文件(流式拷贝,不整体驻留内存)
        Path tmp = null;
        try {
            tmp = Files.createTempFile("zhiwen-", ".pdf");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            List<ParsedSection> sections = new ArrayList<>();
            try (PDDocument doc = Loader.loadPDF(new File(tmp.toString()))) {
                PDFTextStripper stripper = new PDFTextStripper();
                for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(doc);
                    if (text != null && !text.isBlank()) {
                        sections.add(new ParsedSection(text.trim(), page));
                    }
                }
            }
            if (sections.isEmpty()) {
                throw fail("PDF 未提取到任何文本(可能是扫描件,需要 OCR)");
            }
            return sections;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw fail("PDF 解析异常: " + e.getMessage());
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            }
        }
    }
}
