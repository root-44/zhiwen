package com.zhiwen.service.parser;

import com.zhiwen.common.BusinessException;
import com.zhiwen.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析器工厂:按扩展名路由到对应解析器
 * 新增格式只需新增一个 @Component 实现 DocParser,工厂自动收集——开闭原则
 */
@Component
public class ParserFactory {

    private final Map<String, DocParser> parserMap = new HashMap<>();

    /** Spring 注入所有 DocParser 实现,按其声明的支持类型建索引 */
    public ParserFactory(List<DocParser> parsers) {
        for (DocParser parser : parsers) {
            for (String type : parser.supportedTypes()) {
                parserMap.put(type.toLowerCase(), parser);
            }
        }
    }

    public DocParser get(String fileName) {
        String ext = extensionOf(fileName);
        DocParser parser = parserMap.get(ext);
        if (parser == null) {
            throw new BusinessException(ErrorCode.DOC_FORMAT_UNSUPPORTED,
                    "不支持的文件格式 ." + ext + ",当前支持: " + parserMap.keySet());
        }
        return parser;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            throw new BusinessException(ErrorCode.DOC_FORMAT_UNSUPPORTED, "文件名缺少扩展名: " + fileName);
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
