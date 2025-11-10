// src/main/java/com/bkap/aislide/repository/SlideRepository.java
package com.bkap.aislide.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bkap.aislide.entity.SlideGeneration;

public interface SlideRepository extends JpaRepository<SlideGeneration, String> {

    /**
     * LẤY 10 BẢN GHI MỚI NHẤT
     * DÙNG TRONG API: GET /api/slides/recent
     */
    List<SlideGeneration> findTop10ByOrderByCreatedAtDesc();
}