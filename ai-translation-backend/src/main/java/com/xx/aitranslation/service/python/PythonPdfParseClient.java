package com.xx.aitranslation.service.python;

import com.xx.aitranslation.config.PythonPdfParseProperties;
import com.xx.aitranslation.service.python.dto.PythonPdfBlock;
import com.xx.aitranslation.service.python.dto.PythonPdfParseResult;
import com.xx.aitranslation.service.python.dto.PythonPdfParsedText;
import com.xx.aitranslation.service.python.dto.PythonPdfTaskResponse;
import com.xx.aitranslation.service.python.dto.PythonPdfTaskStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PythonPdfParseClient {

    private static final Set<String> TRANSLATABLE_TYPES = Set.of(
            "title", "heading", "paragraph", "list_item", "table", "figure", "header", "footer", "unknown");

    private final PythonPdfParseProperties properties;
    private final RestClient restClient;

    public PythonPdfParseClient(PythonPdfParseProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public PythonPdfParsedText parseWithRetry(byte[] pdfBytes, String fileName) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<String> errors = new ArrayList<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                PythonPdfParsedText parsed = parseOnce(pdfBytes, fileName);
                log.info("Python PDF 解析成功, fileName={}, pythonTaskId={}, attempt={}",
                        fileName, parsed.getTaskId(), attempt);
                return parsed;
            } catch (Exception e) {
                String message = ObjectUtils.isEmpty(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
                errors.add("attempt " + attempt + ": " + message);
                if (attempt < maxAttempts) {
                    log.warn("Python PDF 解析失败, 准备重试, fileName={}, attempt={}/{}, error={}",
                            fileName, attempt, maxAttempts, message);
                } else {
                    log.error("Python PDF 解析连续失败, fileName={}, attempts={}, errors={}",
                            fileName, maxAttempts, errors);
                }
            }
        }
        throw new PythonPdfParseException("Python PDF parse failed after " + maxAttempts
                + " attempts: " + String.join("; ", errors));
    }

    private PythonPdfParsedText parseOnce(byte[] pdfBytes, String fileName) {
        PythonPdfTaskResponse created = createTask(pdfBytes, fileName);
        if (ObjectUtils.isEmpty(created) || ObjectUtils.isEmpty(created.getTaskId())) {
            throw new PythonPdfParseException("Python service returned empty taskId");
        }

        PythonPdfTaskStatusResponse status = waitUntilFinished(created.getTaskId());
        if (!"SUCCEEDED".equals(status.getStatus())) {
            throw new PythonPdfParseException("Python task failed: " + status.getErrorCode()
                    + " " + status.getErrorMessage());
        }

        PythonPdfParseResult result = getResult(created.getTaskId());
        String text = toPlainText(result);
        if (ObjectUtils.isEmpty(text)) {
            throw new PythonPdfParseException("Python parse result has no translatable text");
        }
        return PythonPdfParsedText.builder()
                .taskId(result.getTaskId())
                .text(text)
                .pageCount(result.getPageCount())
                .warnings(result.getWarnings())
                .blocks(result.getBlocks())
                .build();
    }

    private PythonPdfTaskResponse createTask(byte[] pdfBytes, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(pdfBytes, fileName));
        return restClient.post()
                .uri("/api/v1/pdf/parse-tasks")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(PythonPdfTaskResponse.class);
    }

    private PythonPdfTaskStatusResponse waitUntilFinished(String taskId) {
        Instant deadline = Instant.now().plus(properties.getPollTimeout());
        PythonPdfTaskStatusResponse status = null;
        while (Instant.now().isBefore(deadline)) {
            status = getStatus(taskId);
            if (status == null) {
                throw new PythonPdfParseException("Python service returned empty task status");
            }
            if ("SUCCEEDED".equals(status.getStatus()) || "FAILED".equals(status.getStatus())
                    || "CANCELED".equals(status.getStatus())) {
                return status;
            }
            sleep(properties.getPollInterval());
        }
        throw new PythonPdfParseException("Python task polling timed out, taskId=" + taskId
                + ", lastStatus=" + (status == null ? null : status.getStatus()));
    }

    private PythonPdfTaskStatusResponse getStatus(String taskId) {
        return restClient.get()
                .uri("/api/v1/pdf/parse-tasks/{taskId}", taskId)
                .retrieve()
                .body(PythonPdfTaskStatusResponse.class);
    }

    private PythonPdfParseResult getResult(String taskId) {
        return restClient.get()
                .uri("/api/v1/pdf/parse-tasks/{taskId}/result", taskId)
                .retrieve()
                .body(PythonPdfParseResult.class);
    }

    public byte[] downloadAsset(String taskId, String assetId) {
        return restClient.get()
                .uri("/api/v1/pdf/parse-tasks/{taskId}/assets/{assetId}", taskId, assetId)
                .retrieve()
                .body(byte[].class);
    }

    private String toPlainText(PythonPdfParseResult result) {
        if (result == null) {
            return null;
        }
        if (!ObjectUtils.isEmpty(result.getPlainText())) {
            return result.getPlainText();
        }
        if (ObjectUtils.isEmpty(result.getBlocks())) {
            return null;
        }
        return result.getBlocks().stream()
                .filter(block -> block != null && TRANSLATABLE_TYPES.contains(normalizeType(block.getType())))
                .map(this::blockText)
                .filter(text -> !ObjectUtils.isEmpty(text))
                .collect(Collectors.joining("\n\n"));
    }

    private String blockText(PythonPdfBlock block) {
        String type = normalizeType(block.getType());
        String text = ObjectUtils.isEmpty(block.getText()) ? block.getMarkdown() : block.getText();
        if (ObjectUtils.isEmpty(text)) {
            return null;
        }
        if ("title".equals(type)) {
            return "# " + text.strip();
        }
        if ("heading".equals(type)) {
            return "## " + text.strip();
        }
        if ("list_item".equals(type)) {
            String stripped = text.strip();
            return stripped.startsWith("-") || stripped.startsWith("*") ? stripped : "- " + stripped;
        }
        if ("figure".equals(type)) {
            Object assetId = block.getMetadata() == null ? null : block.getMetadata().get("assetId");
            return "[PDF image: " + (assetId == null ? block.getBlockId() : assetId) + "]";
        }
        return text.strip();
    }

    private String normalizeType(String type) {
        return ObjectUtils.isEmpty(type) ? "unknown" : type.trim().toLowerCase();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(100L, duration.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PythonPdfParseException("Interrupted while polling Python PDF task", e);
        }
    }

    private static String stripTrailingSlash(String baseUrl) {
        if (ObjectUtils.isEmpty(baseUrl)) {
            return "http://127.0.0.1:8010";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
