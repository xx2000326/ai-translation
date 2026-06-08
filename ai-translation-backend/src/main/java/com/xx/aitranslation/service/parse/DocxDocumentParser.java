package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import com.xx.aitranslation.enums.ParseGranularity;
import net.sf.okapi.common.LocaleId;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX 解析器。
 * <ul>
 *     <li>按句 / 按段：POI 段落 + SRX 分句（对齐 yunshu WordParserMvp）。</li>
 *     <li>高级拆分（{@link ParseGranularity#STRUCTURE}）：识别标题层级构建章节树，
 *     以最小章节节点为 Chunk，超长按段落切分（详见 {@link DocumentStructureSplitter}）。</li>
 * </ul>
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public ParsedDocument parse(InputStream in, String sourceLang, ParseGranularity granularity) throws Exception {
        try (XWPFDocument document = new XWPFDocument(in)) {
            if (granularity == ParseGranularity.STRUCTURE) {
                return parseByStructure(document);
            }
            return parseByText(document, sourceLang, granularity);
        }
    }

    /**
     * 按句 / 按段拆分：逐段清洗、过滤后按粒度分句。
     */
    private ParsedDocument parseByText(XWPFDocument document, String sourceLang, ParseGranularity granularity) {
        LocaleId locale = ParseLocaleHelper.toLocaleId(sourceLang);
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        int paraOrder = 0;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = ParseTextUtils.removeControlChar(paragraph.getText());
            if (ParseTextUtils.isNonTranslationText(text)) {
                continue;
            }
            List<ParsedSentence> sentences = ParseSrxLoader.split(locale, text, granularity);
            paragraphs.add(new ParsedParagraph(
                    paraOrder++,
                    String.valueOf(paraOrder - 1),
                    "paragraph",
                    text,
                    sentences));
        }
        return new ParsedDocument(paragraphs);
    }

    /**
     * 高级拆分：段落 → {@link StructureBlock} → 章节树 → {@link DocumentChunk}，
     * 每个 Chunk 整体作为一个翻译单元（不做句子级拆分）。
     */
    private ParsedDocument parseByStructure(XWPFDocument document) {
        List<StructureBlock> blocks = new ArrayList<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = ParseTextUtils.removeControlChar(paragraph.getText());
            if (ParseTextUtils.isNonTranslationText(text)) {
                continue;
            }
            int level = resolveHeadingLevel(paragraph, text);
            blocks.add(new StructureBlock(text, level));
        }
        List<DocumentChunk> chunks = DocumentStructureSplitter.split(blocks, DocumentStructureSplitter.DEFAULT_MAX_CHUNK_SIZE);
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        int order = 0;
        for (DocumentChunk chunk : chunks) {
            List<ParsedSentence> sentences = ParseSrxLoader.singleSentence(chunk.content());
            paragraphs.add(new ParsedParagraph(
                    order++,
                    chunk.chunkId(),
                    "chunk",
                    chunk.content(),
                    sentences,
                    chunk.title(),
                    chunk.parentTitle(),
                    chunk.level()));
        }
        return new ParsedDocument(paragraphs);
    }

    /**
     * 解析段落的标题层级：优先用 Word 大纲级别 / 标题样式，回退到文本规则识别。
     */
    private int resolveHeadingLevel(XWPFParagraph paragraph, String text) {
        int outlineLevel = resolveOutlineLevel(paragraph);
        if (outlineLevel > 0) {
            return outlineLevel;
        }
        int styleLevel = HeadingDetector.styleLevel(paragraph.getStyle());
        if (styleLevel > 0) {
            return styleLevel;
        }
        return HeadingDetector.detectLevel(text);
    }

    /**
     * Word 大纲级别（0-based outlineLvl → 1-based 层级），非标题返回 0。
     */
    private int resolveOutlineLevel(XWPFParagraph paragraph) {
        if (ObjectUtils.isEmpty(paragraph.getCTP()) || !paragraph.getCTP().isSetPPr()) {
            return 0;
        }
        CTPPr ppr = paragraph.getCTP().getPPr();
        if (!ppr.isSetOutlineLvl() || ObjectUtils.isEmpty(ppr.getOutlineLvl())) {
            return 0;
        }
        BigInteger val = ppr.getOutlineLvl().getVal();
        if (ObjectUtils.isEmpty(val)) {
            return 0;
        }
        int level = val.intValue();
        if (level < 0 || level >= 9) {
            return 0;
        }
        return level + 1;
    }

    @Override
    public FileType supportType() {
        return FileType.DOCX;
    }
}
