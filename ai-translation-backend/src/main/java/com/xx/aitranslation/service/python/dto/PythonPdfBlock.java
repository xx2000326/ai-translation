package com.xx.aitranslation.service.python.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PythonPdfBlock {
    private String blockId;
    private Integer pageNo;
    private Integer orderNo;
    private String type;
    private String text;
    private String markdown;
    private List<Double> bbox;
    private String sourceParser;
    private Map<String, Object> metadata;
}
