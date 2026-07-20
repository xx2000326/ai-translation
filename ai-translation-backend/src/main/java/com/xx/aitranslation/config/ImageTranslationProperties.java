package com.xx.aitranslation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai.image-translation")
public class ImageTranslationProperties {

    private String baseUrl = "http://127.0.0.1:8000";

    private String apiKey = "";

    private String model = "image-translation-model";

    private String endpointPath = "/v1/images/edits";

    private Duration timeout = Duration.ofMinutes(2);
}
