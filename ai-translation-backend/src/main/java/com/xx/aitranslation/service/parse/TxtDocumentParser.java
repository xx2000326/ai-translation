package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本解析器：按空行分段，连续空行视作一个分隔，忽略纯空白段。
 */
@Component
public class TxtDocumentParser implements DocumentParser {

    @Override
    public List<ParsedBlock> parse(InputStream in) throws Exception {
        List<ParsedBlock> blocks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int order = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (ObjectUtils.isEmpty(line.trim())) {
                    order = flush(blocks, buffer, order);
                } else {
                    if (!buffer.isEmpty()) {
                        buffer.append('\n');
                    }
                    buffer.append(line);
                }
            }
            flush(blocks, buffer, order);
        }
        return blocks;
    }

    /**
     * 将缓冲区累积的段落写入结果（非空白时），并清空缓冲区。
     *
     * @return 下一个可用的顺序号
     */
    private int flush(List<ParsedBlock> blocks, StringBuilder buffer, int order) {
        String text = buffer.toString().trim();
        buffer.setLength(0);
        if (ObjectUtils.isEmpty(text)) {
            return order;
        }
        blocks.add(new ParsedBlock(order, "paragraph", text));
        return order + 1;
    }

    @Override
    public FileType supportType() {
        return FileType.TXT;
    }
}
