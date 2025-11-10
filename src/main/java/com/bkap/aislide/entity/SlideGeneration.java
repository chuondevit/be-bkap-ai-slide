// src/main/java/com/bkap/aislide/entity/SlideGeneration.java
package com.bkap.aislide.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "slides")
@Getter @Setter @NoArgsConstructor
public class SlideGeneration {

    @Id
    private String taskId;

    private String topic;
    private Integer slideCount;

    @Column(nullable = false)
    private String status = "processing";

    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @PrePersist
    private void init() {
        this.taskId = java.util.UUID.randomUUID().toString();
    }
}