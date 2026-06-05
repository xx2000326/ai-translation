package com.xx.aitranslation.service.parse;

import net.sf.okapi.common.LocaleId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseSrxLoaderTest {

    @Test
    void segmentChineseSentences() {
        LocaleId locale = LocaleId.fromString("zh-CN");
        List<ParsedSentence> sentences = ParseSrxLoader.segment(locale, "段落1，你好。第三句。");
        assertEquals(2, sentences.size());
        assertEquals("段落1，你好。", sentences.get(0).sourceText());
        assertEquals("第三句。", sentences.get(1).sourceText());
    }

    @Test
    void segmentMultipleParagraphLines() {
        LocaleId locale = LocaleId.fromString("zh-CN");
        List<ParsedSentence> s1 = ParseSrxLoader.segment(locale, "段落2，早上好。第四句。");
        assertTrue(s1.size() >= 2);
    }
}
