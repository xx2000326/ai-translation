package com.xx.aitranslation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.translation.summary")
public class SummaryProperties {
    private int batchSize = 15;
    private int overlapSentences = 2;
    private int styleGuideMaxSamples = 80;
}
