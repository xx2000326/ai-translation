package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.ParseGranularity;
import lombok.extern.slf4j.Slf4j;
import net.sf.okapi.common.ISegmenter;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.TextPart;
import net.sf.okapi.common.resource.TextUnit;
import net.sf.okapi.lib.segmentation.SRXDocument;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SRX 分句（对齐 yunshu {@code SegmentationUtils.splitByLangCode}）。
 */
@Slf4j
final class ParseSrxLoader {

    private static final SRXDocument SRX = load();

    private ParseSrxLoader() {
    }

    private static SRXDocument load() {
        SRXDocument doc = new SRXDocument();
        try (InputStream in = ParseSrxLoader.class.getResourceAsStream("/srx/langx-3.srx")) {
            if (ObjectUtils.isEmpty(in)) {
                log.error("SRX rules not found: /srx/langx-3.srx");
                return doc;
            }
            doc.loadRules(in);
            log.info("SRX segmentation rules loaded from langx-3.srx");
        } catch (Exception e) {
            log.error("Failed to load SRX rules", e);
        }
        return doc;
    }

    /**
     * 按指定粒度拆分段落文本为翻译单元：
     * <ul>
     *     <li>{@link ParseGranularity#PARAGRAPH}：整段作为单个句子，不再细分。</li>
     *     <li>{@link ParseGranularity#SENTENCE}：SRX 分句。</li>
     * </ul>
     */
    static List<ParsedSentence> split(LocaleId locale, String text, ParseGranularity granularity) {
        if (granularity == ParseGranularity.PARAGRAPH) {
            return singleSentence(text);
        }
        return segment(locale, text);
    }

    /**
     * 整段作为单个翻译单元（按段拆分时使用）。
     */
    static List<ParsedSentence> singleSentence(String text) {
        if (ObjectUtils.isEmpty(text)) {
            return List.of();
        }
        return List.of(new ParsedSentence(0, "0", text));
    }

    /**
     * 按语言码分句（yunshu SegmentationUtils.segment）。
     */
    static List<ParsedSentence> segment(LocaleId locale, String text) {
        if (ObjectUtils.isEmpty(text)) {
            return List.of();
        }
        SRX.setTrimLeadingWhitespaces(true);
        SRX.setTrimTrailingWhitespaces(true);
        ISegmenter segmenter = SRX.compileLanguageRules(locale, null);
        TextUnit tu = new TextUnit("seg", text);
        tu.createSourceSegmentation(segmenter);
        return fromTextUnit(tu, text);
    }

    static ISegmenter createSegmenter(LocaleId locale) {
        SRX.setTrimLeadingWhitespaces(true);
        SRX.setTrimTrailingWhitespaces(true);
        return SRX.compileLanguageRules(locale, null);
    }

    static List<ParsedSentence> fromTextUnit(ITextUnit tu, String fallbackText) {
        List<ParsedSentence> sentences = new ArrayList<>();
        int idx = 0;
        for (TextPart part : tu.getSourceSegments().asList()) {
            String sentText = ParseTextUtils.removeControlChar(part.toString());
            if (ParseTextUtils.isNonTranslationText(sentText)) {
                continue;
            }
            sentences.add(new ParsedSentence(idx, String.valueOf(idx), sentText));
            idx++;
        }
        if (ObjectUtils.isEmpty(sentences) && !ObjectUtils.isEmpty(fallbackText)) {
            sentences.add(new ParsedSentence(0, "0", fallbackText));
        }
        return sentences;
    }
}
