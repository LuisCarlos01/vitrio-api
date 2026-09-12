package dev.vitrio.api.csvimport;

import java.util.List;

public record CsvImportPreviewResponse(List<CsvImportRowResult> rows) {}
