package com.shortlink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUrlRequest(
        @NotBlank @Size(max = 2048) String url,
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,30}$",
                 message = "customAlias must be 4-30 characters: letters, digits, '-' or '_'") String customAlias,
        @Min(1) @Max(3650) Integer expiresInDays) {
}
