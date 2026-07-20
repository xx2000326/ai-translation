package com.xx.aitranslation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.python.pdf-parse")
public class PythonPdfParseProperties {

    /** 是否启用 Python PDF 解析；关闭后直接走 Java 原解析链路。 */
    private boolean enabled = true;

    /** ai-translation-py 服务地址。 */
    private String baseUrl = "http://127.0.0.1:8010";

    /** 创建解析任务接口超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 单次 HTTP 读取超时。 */
    private Duration readTimeout = Duration.ofSeconds(30);

    /** 每次 parse 流程最多调用 Python 的次数。 */
    private int maxAttempts = 3;

    /** 轮询任务状态的最大等待时间。 */
    private Duration pollTimeout = Duration.ofMinutes(5);

    /** 轮询任务状态的间隔。 */
    private Duration pollInterval = Duration.ofSeconds(2);
}
