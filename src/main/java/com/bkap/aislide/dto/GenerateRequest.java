// dto/GenerateRequest.java
package com.bkap.aislide.dto;

import jakarta.validation.constraints.*;

public record GenerateRequest(
    @NotBlank(message = "Chủ đề không được để trống")
    String topic,

    @Min(5) @Max(20)
    Integer slideCount
) {}