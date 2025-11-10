// src/main/java/com/bkap/aislide/service/SlideGenerationTask.java
package com.bkap.aislide.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.bkap.aislide.dto.SlideItem;
import com.bkap.aislide.entity.SlideGeneration;
import com.bkap.aislide.repository.SlideRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlideGenerationTask {

    private static final Logger log = LoggerFactory.getLogger(SlideGenerationTask.class);

    private final AiService ai;
    private final FileStorageService storage;
    private final SlideRepository repo;

    @Async("taskExecutor")
    public void generateAsync(String taskId) {
        SlideGeneration slide = repo.findById(taskId).orElseThrow();

        log.info("Bắt đầu tạo slide | taskId: {} | Chủ đề: \"{}\" | Số slide: {}", taskId, slide.getTopic(), slide.getSlideCount());

        try {
            int count = slide.getSlideCount();
            List<SlideItem> outline = ai.generateSmartOutline(slide.getTopic(), count);
            if (outline == null || outline.isEmpty()) {
                log.warn("Outline rỗng → dùng fallback");
                outline = ai.fallbackOutline(slide.getTopic(), count); // ĐÃ SỬA: CHỈ 2 THAM SỐ
            }

            List<String> htmlSlides = new ArrayList<>();
            int imageCount = 0;
            final int MAX_IMAGES = 5;

            for (int i = 0; i < outline.size(); i++) {
                SlideItem item = outline.get(i);
                String title = item.title() != null && !item.title().isBlank() ? item.title().trim() : "Slide " + (i + 1);
                log.info("Slide {}: {} | Type: {}", (i + 1), title, item.type());

                String content = ai.generateSlideHtml(title, item.type(), slide.getTopic());
                if (content == null || content.trim().isEmpty()) {
                    content = "<p>Nội dung đang tải...</p>";
                }

                String imageHtml = "";
                if (item.type().equals("IMAGE") && imageCount < MAX_IMAGES) {
                    imageCount++;
                    String keyword = ai.generateImageKeyword(title, slide.getTopic()); // ĐÃ CÓ METHOD
                    String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
                    String imgUrl = "https://source.unsplash.com/random/600x800/?" + encoded + "&auto=format&fit=crop";
                    String escapedUrl = imgUrl.replace("&", "&amp;");
                    log.info("IMAGE URL [Slide {}]: {}", (i + 1), imgUrl);
                    imageHtml = "<img src=\"" + escapedUrl + "\" alt=\"" + escape(title) + "\" />";
                }

                String slideHtml = """
                    <div class="slide">
                      <div class="col-left">
                        <h1>%s</h1>
                        <div class="content">%s</div>
                      </div>
                      <div class="col-right">
                        %s
                      </div>
                    </div>
                    """.formatted(escape(title), content, imageHtml);

                htmlSlides.add(slideHtml);
            }

            byte[] pdf = generatePdf(htmlSlides);
            String fileUrl = storage.save(taskId, pdf, "pdf");

            slide.setStatus("completed");
            slide.setFileUrl(fileUrl);
            slide.setCompletedAt(LocalDateTime.now());
            repo.save(slide);

            log.info("HOÀN TẤT! PDF đã lưu: {}", fileUrl);

        } catch (Exception e) {
            log.error("LỖI KHI TẠO PDF: {}", e.getMessage(), e);
            slide.setStatus("failed");
            slide.setErrorMessage("Lỗi: " + e.getMessage());
            repo.save(slide);
        }
    }

    private byte[] generatePdf(List<String> slides) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n")
            .append("  <meta charset=\"UTF-8\"/>\n")
            .append("  <title>Slide AI</title>\n")
            .append("  <style>\n")
            .append("    @page { size: 1920px 1080px; margin: 0; }\n")
            .append("    body { margin: 0; padding: 0; font-family: 'NotoSans', Arial, sans-serif; background: #f9fafb; }\n")
            .append("    .slide { width: 1920px; height: 1080px; page-break-after: always; page-break-inside: avoid; padding: 80px; box-sizing: border-box; display: flex; gap: 60px; align-items: flex-start; background: #ffffff; }\n")
            .append("    .col-left { flex: 1.4; display: flex; flex-direction: column; }\n")
            .append("    .col-left h1 { font-size: 72px; margin: 0 0 50px; color: #1e293b; text-align: center; line-height: 1.2; font-weight: 700; }\n")
            .append("    .content { font-size: 36px; line-height: 1.6; color: #334155; }\n")
            .append("    .content ul { list-style: none; padding: 0; margin: 0; }\n")
            .append("    .content li { margin: 24px 0; position: relative; padding-left: 44px; }\n")
            .append("    .content li:before { content: '•'; color: #f59e0b; position: absolute; left: 0; font-size: 36px; font-weight: bold; top: -4px; }\n")
            .append("    .col-right { flex: 0.6; display: flex; align-items: center; justify-content: center; }\n")
            .append("    .col-right img { width: 100%; max-width: 520px; height: auto; max-height: 720px; border-radius: 28px; box-shadow: 0 25px 50px rgba(0,0,0,0.2); object-fit: cover; }\n")
            .append("    .cta { text-align: center; margin-top: auto; }\n")
            .append("    .cta p { font-size: 52px; margin-bottom: 40px; color: #1e293b; }\n")
            .append("    .cta-button { background: linear-gradient(135deg, #f59e0b, #f97316); color: white; font-weight: 800; padding: 28px 90px; font-size: 54px; border-radius: 80px; border: none; cursor: pointer; box-shadow: 0 15px 35px rgba(249,115,22,0.35); }\n")
            .append("  </style>\n")
            .append("</head>\n<body>\n");

        for (String s : slides) {
            html.append(s).append("\n");
        }

        html.append("</body>\n</html>");

        String finalHtml = html.toString()
            .replaceAll("(?i)<(meta|img|br|hr)([^>]*)(?<!/)>", "<$1$2 />")
            .replaceAll("&(?!amp;|lt;|gt;|quot;|#)", "&amp;")
            .trim();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();

        builder.useFastMode();
        builder.withProducer("AI Slide Pro v19.0");
        builder.withHtmlContent(finalHtml, null);
        builder.toStream(out);

        Path regPath = Paths.get("src/main/resources/fonts/NotoSans-Regular.ttf").toAbsolutePath();
        Path boldPath = Paths.get("src/main/resources/fonts/NotoSans-Bold.ttf").toAbsolutePath();

        try (InputStream reg = Files.exists(regPath) ? Files.newInputStream(regPath) : null;
             InputStream bold = Files.exists(boldPath) ? Files.newInputStream(boldPath) : null) {

            if (reg != null) builder.useFont(() -> reg, "NotoSans", 400, PdfRendererBuilder.FontStyle.NORMAL, true);
            if (bold != null) builder.useFont(() -> bold, "NotoSans", 700, PdfRendererBuilder.FontStyle.NORMAL, true);

            try (var renderer = builder.buildPdfRenderer()) {
                renderer.layout();
                renderer.createPDF(out);
            }
        }

        return out.toByteArray();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}