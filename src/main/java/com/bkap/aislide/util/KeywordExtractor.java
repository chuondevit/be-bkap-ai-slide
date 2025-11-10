// src/main/java/com/bkap/aislide/util/KeywordExtractor.java
package com.bkap.aislide.util;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class KeywordExtractor {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private static final Set<String> KNOWN_TOPICS = Set.of(
            "python","ai","education","nature","technology","science","business","design","music","health",
            "finance","sports","travel","food","machine learning","data science","blockchain","robot",
            "environment","climate","energy","startup","marketing","psychology","history"
    );

    private static final Map<String, String> VI_EN = Map.ofEntries(
            Map.entry("giáo dục","education"),
            Map.entry("trí tuệ nhân tạo","artificial intelligence"),
            Map.entry("học tập","learning"),
            Map.entry("lập trình","programming"),
            Map.entry("python","python"),
            Map.entry("công nghệ","technology"),
            Map.entry("khoa học","science"),
            Map.entry("tự nhiên","nature"),
            Map.entry("môi trường","environment"),
            Map.entry("kinh doanh","business"),
            Map.entry("thiết kế","design"),
            Map.entry("âm nhạc","music"),
            Map.entry("sức khỏe","health"),
            Map.entry("tài chính","finance"),
            Map.entry("thể thao","sports"),
            Map.entry("du lịch","travel"),
            Map.entry("ẩm thực","food"),
            Map.entry("blockchain","blockchain"),
            Map.entry("robot","robot"),
            Map.entry("năng lượng","energy"),
            Map.entry("khí hậu","climate")
    );

    private RestTemplate newRestTemplate() {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        return new RestTemplate(f);
    }
    private final ObjectMapper mapper = new ObjectMapper();

    public List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Text rỗng → fallback 'technology'");
            return List.of("technology");
        }
        if (openAiApiKey != null && !openAiApiKey.isBlank() && openAiApiKey.startsWith("sk-")) {
            try { return extractByAI(text); }
            catch (Exception e) {
                log.error("OpenAI lỗi: {}", e.getMessage());
            }
        }
        return extractSmartFallback(text);
    }

    private List<String> extractByAI(String text) throws Exception {
        String prompt = """
                Từ đoạn văn sau, hãy trích xuất 1-3 từ khóa tiếng Anh ngắn gọn mô tả chủ đề chính.
                Chỉ trả lời bằng từ khóa, cách nhau bởi dấu phẩy. Ví dụ: education, technology, nature

                Đoạn văn:
                """ + text;

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role","user","content", prompt)),
                "temperature", 0.2,
                "max_tokens", 30
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);
        headers.set("User-Agent", "BKAP-AISlide/1.0");

        ResponseEntity<String> res = newRestTemplate().postForEntity(
                "https://api.openai.com/v1/chat/completions",
                new HttpEntity<>(body, headers),
                String.class
        );

        JsonNode root = mapper.readTree(res.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();

        if (content.isEmpty()) return extractSmartFallback(text);

        List<String> kws = Arrays.stream(content.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(k -> k.length() > 2)
                .map(this::mapToKnownTopic)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());

        if (kws.isEmpty()) return extractSmartFallback(text);
        log.info("AI keywords: {}", kws);
        return kws;
    }

    private List<String> extractSmartFallback(String text) {
        String lower = text.toLowerCase();
        for (var e : VI_EN.entrySet()) {
            if (lower.contains(e.getKey())) return List.of(e.getValue());
        }
        for (String t : KNOWN_TOPICS) {
            if (lower.contains(t)) return List.of(t);
        }
        return List.of("technology");
    }

    private String mapToKnownTopic(String k) {
        String lower = k.toLowerCase();
        if (lower.contains("ai") || lower.contains("artificial") || lower.contains("trí tuệ")) return "artificial intelligence";
        if (lower.contains("learn") || lower.contains("study") || lower.contains("học")) return "education";
        if (lower.contains("tech") || lower.contains("công nghệ")) return "technology";
        if (lower.contains("python")) return "python";
        if (lower.contains("data") || lower.contains("machine learning")) return "data science";
        return KNOWN_TOPICS.contains(lower) ? lower : null;
    }
}
