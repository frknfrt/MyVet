package com.vetos.modules.encounter.api.dto;

public record UpdateSoapRequest(String subjective, String objective, String assessment, String plan, boolean aiGenerated) {}
