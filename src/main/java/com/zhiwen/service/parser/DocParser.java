package com.zhiwen.service.parser;

import com.zhiwen.common.BusinessException;
import com.zhiwen.common.ErrorCode;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析器统一接口
 *
 * <p>返回"带页码的文本段"而不是纯文本:PDF 按页拆分保留页码信息,
 * 后续回答可溯源到具体页;其他格式整篇作为一个段(页码为 null)。
 */
public interface DocParser {

    /** 该解析器支持的文件扩展名(小写,不含点) */
    List<String> supportedTypes();

    /**
     * 解析文档
     * @param fileName 原始文件名(用于判断类型)
     * @param in 文件输入流
     * @return 按顺序排列的带页码文本段
     */
    List<ParsedSection> parse(String fileName, InputStream in);

    /** 解析结果:一段文本 + 可选页码 */
    record ParsedSection(String text, Integer pageNo) {
    }

    /** 解析失败统一抛业务异常 */
    default BusinessException fail(String detail) {
        return new BusinessException(ErrorCode.DOC_PARSE_FAIL, detail);
    }
}
