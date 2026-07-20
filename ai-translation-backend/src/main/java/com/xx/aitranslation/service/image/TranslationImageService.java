package com.xx.aitranslation.service.image;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.aitranslation.dto.TranslationImageResponse;
import com.xx.aitranslation.entity.TranslationImage;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.mapper.TranslationImageMapper;
import com.xx.aitranslation.service.python.PythonPdfParseClient;
import com.xx.aitranslation.service.python.dto.PythonPdfBlock;
import com.xx.aitranslation.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationImageService {

    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_TRANSLATING = "TRANSLATING";
    private static final String STATUS_TRANSLATED = "TRANSLATED";
    private static final String STATUS_FAILED = "FAILED";

    private final TranslationImageMapper imageMapper;
    private final FileStorageService fileStorageService;
    private final PythonPdfParseClient pythonPdfParseClient;
    private final ImageTranslationClient imageTranslationClient;
    private final ObjectMapper objectMapper;

    public void clearByTaskId(Long taskId) {
        imageMapper.delete(new LambdaQueryWrapper<TranslationImage>()
                .eq(TranslationImage::getTaskId, taskId));
    }

    public void savePythonPdfImages(Long taskId, Long documentId, TranslationTask task, String pythonTaskId,
                                    List<PythonPdfBlock> blocks) {
        if (ObjectUtils.isEmpty(blocks)) {
            return;
        }
        for (PythonPdfBlock block : blocks) {
            if (!"figure".equalsIgnoreCase(block.getType())) {
                continue;
            }
            saveOneImage(taskId, documentId, task, pythonTaskId, block);
        }
    }

    public List<TranslationImageResponse> listByTaskId(Long taskId) {
        return imageMapper.selectList(new LambdaQueryWrapper<TranslationImage>()
                        .eq(TranslationImage::getTaskId, taskId)
                        .orderByAsc(TranslationImage::getPageNo)
                        .orderByAsc(TranslationImage::getOrderNo))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void saveOneImage(Long taskId, Long documentId, TranslationTask task, String pythonTaskId, PythonPdfBlock block) {
        Map<String, Object> metadata = block.getMetadata();
        String assetId = value(metadata, "assetId");
        if (ObjectUtils.isEmpty(assetId)) {
            return;
        }
        TranslationImage image = new TranslationImage();
        image.setTaskId(taskId);
        image.setDocumentId(documentId);
        image.setPythonTaskId(pythonTaskId);
        image.setBlockId(block.getBlockId());
        image.setPageNo(block.getPageNo());
        image.setOrderNo(block.getOrderNo());
        image.setBboxJson(toJson(block.getBbox()));
        image.setMimeType(value(metadata, "mimeType"));
        image.setWidth(intValue(metadata, "width"));
        image.setHeight(intValue(metadata, "height"));

        try {
            byte[] original = pythonPdfParseClient.downloadAsset(pythonTaskId, assetId);
            String originalName = assetId + extension(image.getMimeType());
            String originalKey = fileStorageService.upload(
                    new ByteArrayInputStream(original),
                    originalName,
                    image.getMimeType());
            image.setOriginalFileKey(originalKey);

            if (!Boolean.TRUE.equals(task.getEnableImageTranslation())) {
                image.setStatus(STATUS_SKIPPED);
                imageMapper.insert(image);
                return;
            }

            image.setStatus(STATUS_TRANSLATING);
            imageMapper.insert(image);
            try {
                byte[] translated = imageTranslationClient.translate(
                        original,
                        originalName,
                        image.getMimeType(),
                        task.getImageTranslationModel(),
                        task.getSourceLang(),
                        task.getTargetLang());
                String translatedKey = fileStorageService.upload(
                        new ByteArrayInputStream(translated),
                        translatedName(originalName),
                        image.getMimeType());
                image.setTranslatedFileKey(translatedKey);
                image.setStatus(STATUS_TRANSLATED);
                image.setErrorMsg(null);
                imageMapper.updateById(image);
            } catch (Exception e) {
                image.setStatus(STATUS_FAILED);
                image.setErrorMsg(trimError(e.getMessage()));
                imageMapper.updateById(image);
                log.error("PDF 图片翻译失败, taskId={}, blockId={}, assetId={}", taskId, block.getBlockId(), assetId, e);
            }
        } catch (Exception e) {
            image.setStatus(STATUS_FAILED);
            image.setErrorMsg(trimError(e.getMessage()));
            imageMapper.insert(image);
            log.error("PDF 图片资产保存失败, taskId={}, blockId={}, assetId={}", taskId, block.getBlockId(), assetId, e);
        }
    }

    private TranslationImageResponse toResponse(TranslationImage image) {
        TranslationImageResponse response = new TranslationImageResponse();
        response.setId(image.getId());
        response.setTaskId(image.getTaskId());
        response.setDocumentId(image.getDocumentId());
        response.setPythonTaskId(image.getPythonTaskId());
        response.setBlockId(image.getBlockId());
        response.setPageNo(image.getPageNo());
        response.setOrderNo(image.getOrderNo());
        response.setBboxJson(image.getBboxJson());
        response.setOriginalFileKey(image.getOriginalFileKey());
        response.setTranslatedFileKey(image.getTranslatedFileKey());
        response.setMimeType(image.getMimeType());
        response.setWidth(image.getWidth());
        response.setHeight(image.getHeight());
        response.setStatus(image.getStatus());
        response.setErrorMsg(image.getErrorMsg());
        return response;
    }

    private String value(Map<String, Object> metadata, String key) {
        if (ObjectUtils.isEmpty(metadata)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private Integer intValue(Map<String, Object> metadata, String key) {
        if (ObjectUtils.isEmpty(metadata)) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String extension(String mimeType) {
        return switch (ObjectUtils.isEmpty(mimeType) ? "" : mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/tiff" -> ".tiff";
            default -> ".bin";
        };
    }

    private String translatedName(String originalName) {
        int dot = originalName.lastIndexOf('.');
        if (dot < 0) {
            return originalName + "_translated";
        }
        return originalName.substring(0, dot) + "_translated" + originalName.substring(dot);
    }

    private String trimError(String message) {
        if (ObjectUtils.isEmpty(message)) {
            return "Image processing failed";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
