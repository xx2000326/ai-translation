package com.xx.aitranslation.service.parse;

import lombok.extern.slf4j.Slf4j;
import net.sf.okapi.common.Event;
import net.sf.okapi.common.ISegmenter;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.common.resource.ITextUnit;
import net.sf.okapi.common.resource.RawDocument;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Okapi 解析公共逻辑（参考 yunshu AbstractOkapiParser + HtmlParser）。
 */
@Slf4j
abstract class OkapiDocumentParser implements DocumentParser {

    protected abstract IFilter createFilter();

    @Override
    public ParsedDocument parse(InputStream in, String sourceLang) throws Exception {
        LocaleId locale = ParseLocaleHelper.toLocaleId(sourceLang);
        ISegmenter segmenter = ParseSrxLoader.createSegmenter(locale);
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        int paraOrder = 0;
        try (IFilter filter = createFilter()) {
            RawDocument raw = new RawDocument(in, StandardCharsets.UTF_8.name(), locale, LocaleId.EMPTY);
            filter.open(raw);
            while (filter.hasNext()) {
                Event event = filter.next();
                if (!event.isTextUnit()) {
                    continue;
                }
                ITextUnit tu = event.getTextUnit();
                if (tu.isEmpty() || !tu.getSource().hasText()) {
                    continue;
                }
                String tuText = ParseTextUtils.removeControlChar(tu.getSource().toString());
                if (ParseTextUtils.isNonTranslationText(tuText)) {
                    continue;
                }
                tu.createSourceSegmentation(segmenter);
                List<ParsedSentence> sentences = ParseSrxLoader.fromTextUnit(tu, tuText);
                paragraphs.add(new ParsedParagraph(
                        paraOrder++,
                        tu.getId(),
                        resolveParaType(tu),
                        tuText,
                        sentences));
            }
        }
        return new ParsedDocument(paragraphs);
    }

    protected String resolveParaType(ITextUnit tu) {
        return "paragraph";
    }
}
