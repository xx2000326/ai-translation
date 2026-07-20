package com.xx.aitranslation.service.python.dto;

import lombok.Data;

import java.util.List;

@Data
public class PythonPdfTaskStatusResponse {
    private String taskId;
    private String status;
    private Integer progress;
    private String currentStep;
    private String errorCode;
    private String errorMessage;
    private List<String> warnings;
}
