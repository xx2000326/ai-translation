package com.xx.aitranslation.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xx.aitranslation.config.ImageTranslationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTranslationClient {

    private final ImageTranslationProperties properties;
    private final ObjectMapper objectMapper;

    public byte[] translate(byte[] imageBytes, String fileName, String mimeType, String model, String sourceLang, String targetLang) {
        if (ObjectUtils.isEmpty(properties.getApiKey())) {
            throw new ImageTranslationException("Image translation API key is not configured");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        RestClient client = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.getBaseUrl()))
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", ObjectUtils.isEmpty(model) ? properties.getModel() : model);
        body.add("image", new NamedByteArrayResource(imageBytes, fileName));
        body.add("prompt", buildPrompt(sourceLang, targetLang));

        String response = client.post()
                .uri(properties.getEndpointPath())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(String.class);
        return decodeImageResponse(response);
    }

    private String buildPrompt(String sourceLang, String targetLang) {
        return "Translate all readable text in this image from "
                + safeLang(sourceLang)
                + " to "
                + safeLang(targetLang)
                + ". Keep layout, colors, charts, and non-text visual elements as unchanged as possible.";
    }

    private byte[] decodeImageResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode first = data.get(0);
                String b64 = first.path("b64_json").asText(null);
                if (!ObjectUtils.isEmpty(b64)) {
                    return Base64.getDecoder().decode(b64);
                }
            }
            throw new ImageTranslationException("Image translation response does not contain data[0].b64_json");
        } catch (ImageTranslationException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageTranslationException("Failed to parse image translation response", e);
        }
    }

    private String safeLang(String lang) {
        return ObjectUtils.isEmpty(lang) ? "auto" : lang;
    }

    private static String stripTrailingSlash(String baseUrl) {
        if (ObjectUtils.isEmpty(baseUrl)) {
            return "http://127.0.0.1:8000";
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
