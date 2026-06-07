package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import com.xx.aitranslation.enums.ParseGranularity;
import net.sf.okapi.common.LocaleId;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX 解析器（对齐 yunshu WordParserMvp：POI 段落 + SegmentationUtils.segment）。
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public ParsedDocument parse(InputStream in, String sourceLang, ParseGranularity granularity) throws Exception {
        LocaleId locale = ParseLocaleHelper.toLocaleId(sourceLang);
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(in)) {
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
        }
        return new ParsedDocument(paragraphs);
    }

    @Override
    public FileType supportType() {
        return FileType.DOCX;
    }
}
