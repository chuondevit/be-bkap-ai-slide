// src/main/java/com/bkap/aislide/service/ImageService.java
package com.bkap.aislide.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.bkap.aislide.util.KeywordExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    @Value("${google.cse.key:}")
    private String googleKey;

    @Value("${google.cse.cx:}")
    private String googleCx;

    private final KeywordExtractor keywordExtractor;
    private final ObjectMapper mapper = new ObjectMapper();

    private RestTemplate newRestTemplate() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        return new RestTemplate(f);
    }

    /** 🔹 Lấy URL ảnh minh họa (ưu tiên Google, fallback Unsplash). */
    public String getImageUrl(String slideTitle, String slideContent) {
        String raw = (slideTitle + " " + slideContent).trim();
        if (raw.isBlank()) return fallbackUnsplash("technology");

        List<String> kws = keywordExtractor.extractKeywords(raw);
        String mainKeyword = kws.isEmpty() ? raw : String.join(" ", kws);
        log.info("🔎 Query image for: {}", mainKeyword);

        String google = findGoogleImage(mainKeyword);
        return (google != null) ? google : fallbackUnsplash(mainKeyword);
    }

    /** 🔍 Gọi Google Custom Search để lấy ảnh “thật”. */
    private String findGoogleImage(String query) {
        if (isBlank(googleKey) || isBlank(googleCx)) {
            log.warn("⚠️ google.cse.key/cx chưa cấu hình → dùng Unsplash fallback");
            return null;
        }

        try {
            // 👉 Nếu muốn tìm toàn web, chỉ cần: String enhanced = query;
            String enhanced = query + " site:unsplash.com OR site:pexels.com OR site:pixabay.com";
            String encoded = URLEncoder.encode(enhanced, StandardCharsets.UTF_8);

            String url = "https://www.googleapis.com/customsearch/v1"
                    + "?key=" + googleKey
                    + "&cx=" + googleCx
                    + "&q=" + encoded
                    + "&searchType=image"
                    + "&num=5"
                    + "&safe=active";

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("User-Agent", "BKAP-AISlide/1.0 (+https://bkap.ai)");

            ResponseEntity<String> res = newRestTemplate().exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                log.warn("Google CSE HTTP {}", res.getStatusCodeValue());
                return null;
            }

            JsonNode root = mapper.readTree(res.getBody());
            JsonNode items = root.path("items");

            if (!items.isArray() || items.size() == 0) {
                log.info("Google CSE không có items cho: {}", query);
                return null;
            }

            // ✅ Duyệt qua tất cả kết quả để lấy link đầu tiên hợp lệ
            for (JsonNode node : items) {
                String link = node.path("link").asText(null);
                if (link == null || !link.startsWith("http")) continue;
                if (link.endsWith(".svg") || link.endsWith(".gif")) continue; // bỏ ảnh vector/animated

                // Nhận mọi ảnh hợp lệ (kể cả .webp, không có đuôi)
                log.info("✅ GOOGLE IMAGE FOUND: {}", link);
                return link;
            }

            log.info("Không tìm thấy ảnh hợp lệ → fallback Unsplash");
            return null;

        } catch (Exception e) {
            log.error("❌ Lỗi Google Image Search: {}", e.getMessage());
            return null;
        }
    }

    /** 🖼️ Fallback Unsplash (nếu Google không trả về ảnh). */
    private String fallbackUnsplash(String query) {
        String url = "https://source.unsplash.com/800x600/?" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.info("🖼️ Unsplash fallback: {}", url);
        return url;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
