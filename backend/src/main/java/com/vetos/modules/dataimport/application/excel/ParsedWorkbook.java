package com.vetos.modules.dataimport.application.excel;

import java.util.List;

public record ParsedWorkbook(List<ParsedRow> owners, List<ParsedRow> patients, List<String> structuralErrors) {}
