package com.zhiwen.service.chunk;

import com.zhiwen.config.RagProperties;
import com.zhiwen.service.parser.DocParser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 切片服务:段落优先聚合 + 超长硬切兜底 + 重叠窗口
 *
 * <p>策略(面试高频):
 * <ul>
 *   <li>固定长度硬切会把句子/段落切断,召回回来的切片语义残缺</li>
 *   <li>这里优先按自然段落聚合,凑满 chunkSize 才输出,段落语义完整</li>
 *   <li>单段本身超过 chunkSize 时才段内硬切,切点间留 overlap 重叠</li>
 *   <li>切片间的 overlap 保证"一条法规/一个概念被切断"时,至少有一片含完整上下文</li>
 * </ul>
 */
@Service
public class ChunkService {

    private final RagProperties props;

    public ChunkService(RagProperties props) {
        this.props = props;
    }

    /** 单个切片结果 */
    public record Chunk(String content, int charCount, Integer pageNo) {
    }

    public List<Chunk> chunk(List<DocParser.ParsedSection> sections) {
        List<Chunk> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Integer bufferPage = null;

        for (DocParser.ParsedSection section : sections) {
            String text = section.text().trim();
            if (text.isEmpty()) {
                continue;
            }
            // 单段超长:先把 buffer 收口,再段内硬切
            if (text.length() > props.getChunkSize()) {
                if (!buffer.isEmpty()) {
                    result.add(new Chunk(buffer.toString().trim(), buffer.length(), bufferPage));
                    buffer.setLength(0);
                    bufferPage = null;
                }
                result.addAll(hardSplit(text, section.pageNo()));
                continue;
            }
            // 常规段:加入 buffer,满了就收口一个切片
            if (!buffer.isEmpty() && buffer.length() + text.length() + 1 > props.getChunkSize()) {
                result.add(new Chunk(buffer.toString().trim(), buffer.length(), bufferPage));
                // 保留尾部 overlap,让下一片与前片语义衔接
                String tail = tailOverlap(buffer);
                buffer.setLength(0);
                buffer.append(tail);
                if (bufferPage == null) {
                    bufferPage = section.pageNo();
                }
            }
            if (buffer.isEmpty()) {
                bufferPage = section.pageNo();   // 新片起始页码
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(text);
        }
        if (!buffer.isEmpty()) {
            result.add(new Chunk(buffer.toString().trim(), buffer.length(), bufferPage));
        }
        return result;
    }

    /** 段内硬切:步长 = chunkSize - overlap,切片间留重叠 */
    private List<Chunk> hardSplit(String text, Integer pageNo) {
        List<Chunk> pieces = new ArrayList<>();
        int step = props.getChunkSize() - props.getChunkOverlap();
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + props.getChunkSize(), text.length());
            pieces.add(new Chunk(text.substring(start, end), end - start, pageNo));
            if (end == text.length()) {
                break;
            }
        }
        return pieces;
    }

    /** 取 buffer 尾部 overlap 字符(尽量从句子边界开始,避免半句开头) */
    private String tailOverlap(StringBuilder buffer) {
        int from = Math.max(0, buffer.length() - props.getChunkOverlap());
        String tail = buffer.substring(from);
        int brk = tail.indexOf('\n');
        if (brk >= 0 && brk < tail.length() - 1) {
            tail = tail.substring(brk + 1);   // 对齐到下一行开头
        }
        return tail;
    }
}
