package com.xx.aitranslation.service.export;

import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.enums.ExportFormat;
import org.springframework.util.ObjectUtils;

import java.util.List;

public interface DocumentExporter {

    ExportFormat format();

    byte[] export(List<TranslationSentence> sentences);

    String contentType();

    String fileExtension();

    default String getText(TranslationSentence sent) {
        if (!ObjectUtils.isEmpty(sent.getFinalText())) {
            return sent.getFinalText();
        }
        if (!ObjectUtils.isEmpty(sent.getReviewedText())) {
            return sent.getReviewedText();
        }
        return sent.getTranslatedText();
    }
}
