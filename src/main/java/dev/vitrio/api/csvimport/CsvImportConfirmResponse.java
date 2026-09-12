package dev.vitrio.api.csvimport;

import java.util.List;

public record CsvImportConfirmResponse(List<CsvImportConfirmRowResult> rows) {}
