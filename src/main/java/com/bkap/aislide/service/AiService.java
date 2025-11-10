// src/main/java/com/bkap/aislide/service/AiService.java
package com.bkap.aislide.service;

import com.bkap.aislide.dto.SlideItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    @Value("${openai.api-key}") private String apiKey;
    @Value("${openai.model}") private String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    private void init() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("openai.api-key không được để trống");
        }
    }

    public List<SlideItem> generateSmartOutline(String topic, int count) {
        String prompt = """
            TẠO CHÍNH XÁC %d SLIDE VỀ CHỦ ĐỀ: "%s"
            TRẢ VỀ DUY NHẤT MỘT MẢNG JSON (KHÔNG ```):
            [
              {"title": "AI Thay Đổi Cách Học", "type": "TITLE"},
              {"title": "Công Cụ AI Mạnh Mẽ", "type": "BULLET"},
              {"title": "Ứng Dụng Thực Tế", "type": "IMAGE"},
              ...
            ]
            - TITLE: 3-7 từ, KHÔNG SỐ, KHÔNG LẶP.
            - TYPE: TITLE, BULLET, IMAGE, CTA.
            - CHỈ 3-4 SLIDE IMAGE.
            - KHÔNG LẶP Ý, NGẮN GỌN, HẤP DẪN.
            """.formatted(count, topic);

        String json = callGpt(prompt);
        json = cleanJsonResponse(json);

        try {
            List<SlideItem> slides = mapper.readValue(json, new TypeReference<List<SlideItem>>() {});
            Set<String> usedTitles = new HashSet<>();
            List<SlideItem> result = new ArrayList<>();
            int imageCount = 0;

            for (SlideItem s : slides) {
                if (result.size() >= count) break;
                String t = s.title();
                if (t == null || t.isBlank() || usedTitles.contains(t) || t.length() > 45 || t.matches(".*\\d.*")) continue;
                usedTitles.add(t);

                if (s.type().equals("IMAGE") && imageCount >= 4) continue;
                if (s.type().equals("IMAGE")) imageCount++;

                result.add(s);
            }

            while (result.size() < count - 1) {
                result.add(new SlideItem("Khám Phá Thêm", "BULLET"));
            }
            if (result.size() < count) {
                result.add(new SlideItem("Bắt Đầu Ngay!", "CTA"));
            }

            return result;
        } catch (Exception e) {
            log.warn("Parse outline lỗi → dùng fallback", e);
            return fallbackOutline(topic, count);
        }
    }

    // ĐÃ SỬA: public + XÓA \
    public List<SlideItem> fallbackOutline(String topic, int count) {
        List<SlideItem> list = List.of(
            new SlideItem("AI Thay Đổi Giáo Dục", "TITLE"),
            new SlideItem("Học Cá Nhân Hóa", "BULLET"),
            new SlideItem("AI Trong Lớp Học", "IMAGE"),
            new SlideItem("Hỗ Trợ Giáo Viên", "BULLET"),
            new SlideItem("Tương Lai Hứa Hẹn", "BULLET"),
            new SlideItem("Học Tập 24/7", "IMAGE"),
            new SlideItem("Phân Tích Thông Minh", "BULLET"),
            new SlideItem("Tăng Tương Tác", "BULLET"),
            new SlideItem("Bắt Đầu Hành Trình", "CTA")
        );
        return list.subList(0, Math.min(count, list.size()));
    }

    public String generateSlideHtml(String title, String type, String fullOutline) {
        String prompt = """
            TẠO NỘI DUNG CHO SLIDE:
            Tiêu đề: "%s"
            Chủ đề: %s
            Loại: %s
            YÊU CẦU:
            - TITLE: 2-3 câu ngắn gọn, hấp dẫn.
            - BULLET: 5 điểm, mỗi điểm 1 câu (tối đa 12 từ).
            - IMAGE: Không trả về gì.
            - CTA: 1 câu + nút.
            TRẢ VỀ CHỈ <div class="content">...</div>
            """.formatted(title, fullOutline, type);

        String raw = callGpt(prompt);
        String content = extractContentDiv(raw);
        if (content == null || content.trim().isEmpty()) {
            content = fallbackContent(title, type);
        }

        return safeContent(content);
    }

    private String fallbackContent(String title, String type) {
        return switch (type) {
            case "TITLE" -> "<p>Khám phá <strong>" + title + "</strong></p><p>Thay đổi cách học mãi mãi.</p>";
            case "BULLET" -> """
                <ul>
                  <li>AI phân tích phong cách học riêng</li>
                  <li>Chatbot hỗ trợ 24/7 mọi lúc</li>
                  <li>Tự động chấm bài siêu nhanh</li>
                  <li>Phát hiện điểm yếu kịp thời</li>
                  <li>Đề xuất lộ trình cá nhân</li>
                </ul>
                """;
            case "CTA" -> """
                <div style="text-align:center;">
                  <p style="font-size:40px;margin-bottom:40px;">Bắt đầu ngay hôm nay!</p>
                  <button class="cta-button">Khám Phá!</button>
                </div>
                """;
            default -> "";
        };
    }

    private String safeContent(String html) {
        if (html == null || html.isBlank()) return "";
        Safelist safelist = Safelist.relaxed()
            .addTags("div", "p", "ul", "li", "strong", "button")
            .addAttributes("button", "class")
            .preserveRelativeLinks(true);
        return Jsoup.clean(html, safelist);
    }

    public String generateImageKeyword(String title, String topic) {
        String prompt = "Tạo 1 từ khóa tiếng Việt tìm ảnh Unsplash liên quan đến: \"" + title + "\" trong chủ đề \"" + topic + "\". Chỉ trả 1 cụm từ ngắn, không dấu chấm.";
        try {
            String raw = callGpt(prompt);
            String keyword = raw.trim()
                .replaceAll("[^\\p{L}0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
            log.info("Từ khóa ảnh (tiếng Việt): {}", keyword);
            return keyword.isEmpty() ? removeVietnameseAccents(topic) : keyword;
        } catch (Exception e) {
            String fallback = removeVietnameseAccents(title + " " + topic);
            log.warn("Fallback từ khóa ảnh: {}", fallback);
            return fallback;
        }
    }

    private String removeVietnameseAccents(String str) {
        return java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("đ", "d").replace("Đ", "D")
            .toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public String generateImage(String title) {
        try {
            String query = URLEncoder.encode(title + " education AI minimal illustration flat design", StandardCharsets.UTF_8);
            String url = "https://www.pinterest.com/search/pins/?q=" + query;

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            String html = response.getBody();

            if (html != null) {
var matcher = Pattern.compile("src=\\[?(https://i\\.pinimg\\.com/[^\"]+\\.jpg)\"").matcher(html);
                while (matcher.find()) {
                    String imgUrl = matcher.group(1);
                    if (imgUrl.contains("236x") || imgUrl.contains("474x")) continue;
                    return imgUrl;
                }
            }
        } catch (Exception e) {
            log.warn("Pinterest lỗi → dùng fallback", e);
        }
        return "https://picsum.photos/seed/" + Math.abs(title.hashCode()) + "/800/800?blur=1";
    }

    private String callGpt(String prompt) {
        var body = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.7,
            "max_tokens", 800
        );
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
            "https://api.openai.com/v1/chat/completions",
            new HttpEntity<>(body, headers),
            Map.class
        );

        Map<String, Object> data = resp.getBody();
        if (data == null || !data.containsKey("choices")) {
            throw new RuntimeException("OpenAI lỗi");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private String cleanJsonResponse(String text) {
        return text.trim().replaceAll("(?s)^```json\\s*|```$", "").trim();
    }

    private String extractContentDiv(String html) {
        var m = Pattern.compile("<div[^>]*class=[^>]*content[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1).trim() : html.trim();
    }
}