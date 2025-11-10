// dto/SlideItem.java
package com.bkap.aislide.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SlideItem(
    String title,
    @JsonProperty("type") String type
) {}