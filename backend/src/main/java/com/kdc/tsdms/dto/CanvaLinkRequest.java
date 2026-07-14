package com.kdc.tsdms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CanvaLinkRequest(
        @NotBlank String fileName,

        @NotBlank @Pattern(regexp = "^https://((www\\.)?canva\\.com|canva\\.link)/.*$", message = "Invalid Canva URL")
        String canvaUrl) {}
