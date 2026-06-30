package com.xx.aitranslation.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TranslationGraphConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void graphBeanShouldCompile() {
        CompiledGraph graph = applicationContext.getBean("translationGraph", CompiledGraph.class);
        assertNotNull(graph, "Graph Bean 应成功创建");
    }

    @Test
    void graphShouldInvokeWithMinimalState() throws Exception {
        CompiledGraph graph = applicationContext.getBean("translationGraph", CompiledGraph.class);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("enableReview", false);
        inputs.put("enableSummary", false);

        var result = graph.invoke(inputs);
        assertTrue(result.isPresent());
    }
}
