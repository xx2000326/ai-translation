package com.xx.aitranslation.service.export;

import com.xx.aitranslation.entity.TranslationSegment;
import com.xx.aitranslation.enums.ExportFormat;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 译文导出器：将已按 orderNo 升序排列的段落最终文本导出为指定格式的字节流。
 * <p>
 * 纯结构化导出，不还原源文档样式。每段最终文本通过 {@link #getText(TranslationSegment)} 取得。
 */
public interface DocumentExporter {

    /**
     * 支持的导出格式。
     */
    ExportFormat format();

    /**
     * 导出段落集合为字节流（调用方需保证段落已按 orderNo 升序排列）。
     *
     * @param segments 段落列表
     * @return 导出内容字节
     */
    byte[] export(List<TranslationSegment> segments);

    /**
     * 响应内容类型。
     */
    String contentType();

    /**
     * 导出文件扩展名（不含点号）。
     */
    String fileExtension();

    /**
     * 解析段落最终文本：finalText 非空取 finalText，否则 reviewedText 非空取 reviewedText，否则取 translatedText。
     *
     * @param seg 段落
     * @return 最终文本
     */
    default String getText(TranslationSegment seg) {
        if (!ObjectUtils.isEmpty(seg.getFinalText())) {
            return seg.getFinalText();
        }
        if (!ObjectUtils.isEmpty(seg.getReviewedText())) {
            return seg.getReviewedText();
        }
        return seg.getTranslatedText();
    }
}
