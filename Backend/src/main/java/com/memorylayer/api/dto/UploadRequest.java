package com.memorylayer.api.dto;

import java.util.List;

public record UploadRequest(List<UploadFileRequest> files) {
}
