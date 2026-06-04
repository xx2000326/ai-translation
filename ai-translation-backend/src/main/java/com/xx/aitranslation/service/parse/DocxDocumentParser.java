package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX 解析器：基于 POI 遍历段落，每个非空段落文本为一段（标题样式不细分）。
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public List<ParsedBlock> parse(InputStream in) throws Exception {
        List<ParsedBlock> blocks = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(in)) {
            int order = 0;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (ObjectUtils.isEmpty(text) || ObjectUtils.isEmpty(text.trim())) {
                    continue;
                }
                blocks.add(new ParsedBlock(order++, "paragraph", text.trim()));
            }
        }
        return blocks;
    }

    @Override
    public FileType supportType() {
        return FileType.DOCX;
    }
}
