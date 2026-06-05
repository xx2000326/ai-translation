package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import net.sf.okapi.common.LocaleId;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT 解析器（对齐 yunshu PlainTextParserMvp：按行段落 + SegmentationUtils.segment）。
 */
@Component
public class OkapiTxtDocumentParser implements DocumentParser {

    @Override
    public ParsedDocument parse(InputStream in, String sourceLang) throws Exception {
        LocaleId locale = ParseLocaleHelper.toLocaleId(sourceLang);
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        int paraOrder = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String text = ParseTextUtils.removeControlChar(line);
                if (ParseTextUtils.isNonTranslationText(text)) {
                    continue;
                }
                List<ParsedSentence> sentences = ParseSrxLoader.segment(locale, text);
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
        return FileType.TXT;
    }
}
