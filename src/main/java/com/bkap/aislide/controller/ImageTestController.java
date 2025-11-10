// src/main/java/com/bkap/aislide/controller/ImageTestController.java
package com.bkap.aislide.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bkap.aislide.service.ImageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImageTestController {

    private final ImageService imageService;

    @GetMapping("/test-image")
    public ResponseEntity<Map<String, Object>> testImage(
            @RequestParam(defaultValue = "AI thay đổi cách học tập") String text) {

        String finalUrl = imageService.getImageUrl("Tiêu đề slide", text);

        Map<String, Object> response = Map.of(
                "input_text", text,
                "final_image_url", finalUrl,
                "preview_in_browser", finalUrl,
                "source", getSourceName(finalUrl),
                "status", "success",
                "tip", "Nếu ảnh ĐẸP → gọi /api/slides/create-and-get để tạo PDF ngay!"
        );
        return ResponseEntity.ok(response);
    }

    private String getSourceName(String url) {
        if (url == null) return "Unknown";
        if (url.contains("google") || url.contains("customsearch")) return "Google CSE";
        if (url.contains("unsplash")) return "Unsplash";
        if (url.contains("picsum")) return "Picsum (fallback)";
        if (url.startsWith("/")) return "Local";
        return "Web";
    }
}
