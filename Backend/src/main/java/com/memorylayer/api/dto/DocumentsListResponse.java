package com.memorylayer.api.dto;

import java.util.List;

public record DocumentsListResponse(List<DocumentSummary> items, String nextCursor) {
}
