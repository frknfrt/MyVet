package com.vetos.modules.lab.application.dto;

public record LabResultFileContent(String fileName, String contentType, byte[] content) {}
