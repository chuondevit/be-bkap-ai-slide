// src/main/java/com/bkap/aislide/api/SlideApi.java
package com.bkap.aislide.api;

import java.io.IOException;
import java.nio.file.Paths;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bkap.aislide.dto.ApiResponse;
import com.bkap.aislide.dto.GenerateRequest;
import com.bkap.aislide.entity.SlideGeneration;
import com.bkap.aislide.repository.SlideRepository;
import com.bkap.aislide.service.SlideGenerationTask;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/slides")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SlideApi {

    private final SlideRepository repo;
    private final SlideGenerationTask taskService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<SlideGeneration>> generate(@RequestBody GenerateRequest req) {
        try {
            SlideGeneration slide = new SlideGeneration();
            slide.setTopic(req.topic());
            slide.setSlideCount(req.slideCount());
            repo.save(slide);

            taskService.generateAsync(slide.getTaskId());

            return ResponseEntity.ok(ApiResponse.success(slide));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<SlideGeneration> getStatus(@PathVariable String taskId) {
        return repo.findById(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws IOException {
        var filePath = Paths.get("./uploads").resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}