package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.ParagraphDetailResponse;
import com.xx.aitranslation.dto.SentenceView;
import com.xx.aitranslation.entity.TranslationDocument;
import com.xx.aitranslation.entity.TranslationParagraph;
import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.mapper.TranslationDocumentMapper;
import com.xx.aitranslation.mapper.TranslationParagraphMapper;
import com.xx.aitranslation.mapper.TranslationSentenceMapper;
import com.xx.aitranslation.service.parse.ParsedDocument;
import com.xx.aitranslation.service.parse.ParsedParagraph;
import com.xx.aitranslation.service.parse.ParsedSentence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private final TranslationDocumentMapper documentMapper;
    private final TranslationParagraphMapper paragraphMapper;
    private final TranslationSentenceMapper sentenceMapper;

    public void clearParseResult(Long taskId) {
        TranslationDocument doc = findDocumentByTaskId(taskId);
        if (ObjectUtils.isEmpty(doc)) {
            return;
        }
        List<Long> paragraphIds = paragraphMapper.selectList(new LambdaQueryWrapper<TranslationParagraph>()
                        .eq(TranslationParagraph::getDocumentId, doc.getId()))
                .stream().map(TranslationParagraph::getId).toList();
        if (!ObjectUtils.isEmpty(paragraphIds)) {
            sentenceMapper.delete(new LambdaQueryWrapper<TranslationSentence>()
                    .in(TranslationSentence::getParagraphId, paragraphIds));
        }
        paragraphMapper.delete(new LambdaQueryWrapper<TranslationParagraph>()
                .eq(TranslationParagraph::getDocumentId, doc.getId()));
        documentMapper.deleteById(doc.getId());
    }

    public TranslationDocument saveParsedDocument(Long taskId, TranslationTask task, ParsedDocument parsed) {
        if (ObjectUtils.isEmpty(parsed) || ObjectUtils.isEmpty(parsed.paragraphs())) {
            throw new BizException("parse.no.content");
        }
        int totalSentences = parsed.paragraphs().stream()
                .mapToInt(p -> ObjectUtils.isEmpty(p.sentences()) ? 0 : p.sentences().size())
                .sum();
        if (totalSentences == 0) {
            throw new BizException("parse.no.content");
        }

        TranslationDocument document = new TranslationDocument();
        document.setTaskId(taskId);
        document.setFileName(task.getSourceFileName());
        document.setFileKey(task.getSourceFileKey());
        document.setFileType(task.getSourceFileType());
        document.setParagraphCount(parsed.paragraphs().size());
        document.setSentenceCount(totalSentences);
        documentMapper.insert(document);

        int globalOrder = 0;
        for (ParsedParagraph para : parsed.paragraphs()) {
            TranslationParagraph paragraph = new TranslationParagraph();
            paragraph.setDocumentId(document.getId());
            paragraph.setOrderNo(para.orderNo());
            paragraph.setParaPosition(para.paraPosition());
            paragraph.setParaType(para.paraType());
            paragraph.setOriginalText(para.originalText());
            if (!ObjectUtils.isEmpty(para.level())) {
                paragraph.setChunkId(para.paraPosition());
                paragraph.setTitle(para.title());
                paragraph.setParentTitle(para.parentTitle());
                paragraph.setSectionId(para.sectionId());
                paragraph.setSectionTitle(para.sectionTitle());
                paragraph.setLevel(para.level());
            }
            paragraphMapper.insert(paragraph);

            if (ObjectUtils.isEmpty(para.sentences())) {
                continue;
            }
            for (ParsedSentence sent : para.sentences()) {
                TranslationSentence sentence = new TranslationSentence();
                sentence.setParagraphId(paragraph.getId());
                sentence.setOrderNo(globalOrder++);
                sentence.setSentIndex(sent.sentIndex());
                sentence.setSentPosition(sent.sentPosition());
                sentence.setOriginalText(sent.sourceText());
                sentenceMapper.insert(sentence);
            }
        }
        return document;
    }

    public List<ParagraphDetailResponse> listParagraphDetails(Long taskId) {
        TranslationDocument doc = requireDocument(taskId);
        List<TranslationParagraph> paragraphs = paragraphMapper.selectList(new LambdaQueryWrapper<TranslationParagraph>()
                .eq(TranslationParagraph::getDocumentId, doc.getId())
                .orderByAsc(TranslationParagraph::getOrderNo));
        if (ObjectUtils.isEmpty(paragraphs)) {
            return List.of();
        }
        Map<Long, List<TranslationSentence>> sentenceMap = loadSentenceMap(paragraphs);
        List<ParagraphDetailResponse> result = new ArrayList<>();
        for (TranslationParagraph para : paragraphs) {
            ParagraphDetailResponse item = new ParagraphDetailResponse();
            item.setId(para.getId());
            item.setOrderNo(para.getOrderNo());
            item.setParaType(para.getParaType());
            item.setOriginalText(para.getOriginalText());
            List<TranslationSentence> sents = sentenceMap.getOrDefault(para.getId(), List.of());
            for (TranslationSentence sent : sents) {
                ParagraphDetailResponse.SentenceItem si = new ParagraphDetailResponse.SentenceItem();
                si.setId(sent.getId());
                si.setOrderNo(sent.getOrderNo());
                si.setSentIndex(sent.getSentIndex());
                si.setOriginalText(sent.getOriginalText());
                si.setTranslatedText(sent.getTranslatedText());
                si.setReviewedText(sent.getReviewedText());
                si.setFinalText(sent.getFinalText());
                si.setReviewScore(sent.getReviewScore());
                si.setReviewAdvice(sent.getReviewAdvice());
                si.setReviewFlag(sent.getReviewFlag());
                item.getSentences().add(si);
            }
            result.add(item);
        }
        return result;
    }

    public List<TranslationSentence> listSentences(Long taskId) {
        TranslationDocument doc = findDocumentByTaskId(taskId);
        if (ObjectUtils.isEmpty(doc)) {
            return List.of();
        }
        List<Long> paragraphIds = paragraphMapper.selectList(new LambdaQueryWrapper<TranslationParagraph>()
                        .eq(TranslationParagraph::getDocumentId, doc.getId()))
                .stream().map(TranslationParagraph::getId).toList();
        if (ObjectUtils.isEmpty(paragraphIds)) {
            return List.of();
        }
        return sentenceMapper.selectList(new LambdaQueryWrapper<TranslationSentence>()
                .in(TranslationSentence::getParagraphId, paragraphIds)
                .orderByAsc(TranslationSentence::getOrderNo));
    }

    public List<SentenceView> listSentenceViews(Long taskId) {
        TranslationDocument doc = findDocumentByTaskId(taskId);
        if (ObjectUtils.isEmpty(doc)) {
            return List.of();
        }
        List<TranslationParagraph> paragraphs = paragraphMapper.selectList(new LambdaQueryWrapper<TranslationParagraph>()
                .eq(TranslationParagraph::getDocumentId, doc.getId()));
        Map<Long, TranslationParagraph> paraMap = new HashMap<>();
        for (TranslationParagraph p : paragraphs) {
            paraMap.put(p.getId(), p);
        }
        List<TranslationSentence> sentences = listSentences(taskId);
        List<SentenceView> views = new ArrayList<>();
        for (TranslationSentence sent : sentences) {
            SentenceView view = new SentenceView();
            view.setId(sent.getId());
            view.setParagraphId(sent.getParagraphId());
            view.setOrderNo(sent.getOrderNo());
            TranslationParagraph para = paraMap.get(sent.getParagraphId());
            view.setBlockType(ObjectUtils.isEmpty(para) ? "paragraph" : para.getParaType());
            if (!ObjectUtils.isEmpty(para)) {
                view.setTitle(para.getTitle());
                view.setParentTitle(para.getParentTitle());
                view.setSectionId(para.getSectionId());
                view.setSectionTitle(para.getSectionTitle());
                view.setLevel(para.getLevel());
            }
            view.setOriginalText(sent.getOriginalText());
            view.setTranslatedText(sent.getTranslatedText());
            view.setReviewedText(sent.getReviewedText());
            view.setFinalText(sent.getFinalText());
            view.setReviewScore(sent.getReviewScore());
            view.setReviewAdvice(sent.getReviewAdvice());
            view.setReviewFlag(sent.getReviewFlag());
            views.add(view);
        }
        return views;
    }

    public void updateSentence(TranslationSentence sentence) {
        sentenceMapper.updateById(sentence);
    }

    /**
     * 清空任务下所有句子的机翻 / 审校 / 定稿结果，用于重新翻译前的全量复位。
     * 原文（{@code original_text}）保持不变。
     */
    public void resetTranslations(Long taskId) {
        TranslationDocument doc = findDocumentByTaskId(taskId);
        if (ObjectUtils.isEmpty(doc)) {
            return;
        }
        List<Long> paragraphIds = paragraphMapper.selectList(new LambdaQueryWrapper<TranslationParagraph>()
                        .eq(TranslationParagraph::getDocumentId, doc.getId()))
                .stream().map(TranslationParagraph::getId).toList();
        if (ObjectUtils.isEmpty(paragraphIds)) {
            return;
        }
        sentenceMapper.update(null, new LambdaUpdateWrapper<TranslationSentence>()
                .in(TranslationSentence::getParagraphId, paragraphIds)
                .set(TranslationSentence::getTranslatedText, null)
                .set(TranslationSentence::getReviewedText, null)
                .set(TranslationSentence::getFinalText, null)
                .set(TranslationSentence::getReviewScore, null)
                .set(TranslationSentence::getReviewAdvice, null)
                .set(TranslationSentence::getReviewFlag, false));
    }

    public TranslationSentence getSentence(Long sentenceId) {
        TranslationSentence sentence = sentenceMapper.selectById(sentenceId);
        if (ObjectUtils.isEmpty(sentence)) {
            throw new BizException("sentence.not.found");
        }
        return sentence;
    }

    public void saveFinal(Long sentenceId, String finalText) {
        TranslationSentence sentence = getSentence(sentenceId);
        sentence.setFinalText(finalText);
        sentenceMapper.updateById(sentence);
    }

    public String resolveFinalText(TranslationSentence sentence) {
        if (!ObjectUtils.isEmpty(sentence.getFinalText())) {
            return sentence.getFinalText();
        }
        if (!ObjectUtils.isEmpty(sentence.getReviewedText())) {
            return sentence.getReviewedText();
        }
        return sentence.getTranslatedText();
    }

    private TranslationDocument requireDocument(Long taskId) {
        TranslationDocument doc = findDocumentByTaskId(taskId);
        if (ObjectUtils.isEmpty(doc)) {
            throw new BizException("document.not.found");
        }
        return doc;
    }

    private TranslationDocument findDocumentByTaskId(Long taskId) {
        return documentMapper.selectOne(new LambdaQueryWrapper<TranslationDocument>()
                .eq(TranslationDocument::getTaskId, taskId));
    }

    private Map<Long, List<TranslationSentence>> loadSentenceMap(List<TranslationParagraph> paragraphs) {
        List<Long> ids = paragraphs.stream().map(TranslationParagraph::getId).toList();
        List<TranslationSentence> sentences = sentenceMapper.selectList(new LambdaQueryWrapper<TranslationSentence>()
                .in(TranslationSentence::getParagraphId, ids)
                .orderByAsc(TranslationSentence::getSentIndex));
        Map<Long, List<TranslationSentence>> map = new HashMap<>();
        for (TranslationSentence sent : sentences) {
            map.computeIfAbsent(sent.getParagraphId(), k -> new ArrayList<>()).add(sent);
        }
        return map;
    }
}
