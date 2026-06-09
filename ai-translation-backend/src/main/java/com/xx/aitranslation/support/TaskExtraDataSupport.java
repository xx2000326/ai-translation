package com.xx.aitranslation.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xx.aitranslation.dto.TaskExtraData;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class TaskExtraDataSupport {

    private static final int MIN_HEADING_LEVEL = 1;
    private static final int MAX_HEADING_LEVEL = 6;
    private static final int DEFAULT_HEADING_LEVEL = 1;

    private final ObjectMapper objectMapper;

    public TaskExtraDataSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    TaskExtraDataSupport() {
        this.objectMapper = new ObjectMapper();
    }

    public String mergeHeadingLevel(String existingJson, Integer headingLevel) {
        if (ObjectUtils.isEmpty(headingLevel)) {
            return existingJson;
        }
        try {
            ObjectNode root = readRootObject(existingJson);
            ObjectNode chunk = root.has("chunk") && root.get("chunk").isObject()
                    ? (ObjectNode) root.get("chunk")
                    : root.putObject("chunk");
            chunk.put("headingLevel", clampHeadingLevel(headingLevel));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            ObjectNode root = objectMapper.createObjectNode();
            root.putObject("chunk").put("headingLevel", clampHeadingLevel(headingLevel));
            try {
                return objectMapper.writeValueAsString(root);
            } catch (Exception ex) {
                return "{\"chunk\":{\"headingLevel\":" + clampHeadingLevel(headingLevel) + "}}";
            }
        }
    }

    public int resolveHeadingLevel(String extraDataJson) {
        TaskExtraData data = parse(extraDataJson);
        if (ObjectUtils.isEmpty(data) || ObjectUtils.isEmpty(data.getChunk())
                || ObjectUtils.isEmpty(data.getChunk().getHeadingLevel())) {
            return DEFAULT_HEADING_LEVEL;
        }
        return clampHeadingLevel(data.getChunk().getHeadingLevel());
    }

    public int clampHeadingLevel(Integer level) {
        if (ObjectUtils.isEmpty(level)) {
            return DEFAULT_HEADING_LEVEL;
        }
        return Math.max(MIN_HEADING_LEVEL, Math.min(MAX_HEADING_LEVEL, level));
    }

    public TaskExtraData parse(String extraDataJson) {
        if (ObjectUtils.isEmpty(extraDataJson)) {
            return new TaskExtraData();
        }
        try {
            return objectMapper.readValue(extraDataJson, TaskExtraData.class);
        } catch (Exception e) {
            return new TaskExtraData();
        }
    }

    private ObjectNode readRootObject(String existingJson) throws Exception {
        if (ObjectUtils.isEmpty(existingJson)) {
            return objectMapper.createObjectNode();
        }
        return (ObjectNode) objectMapper.readTree(existingJson);
    }
}
