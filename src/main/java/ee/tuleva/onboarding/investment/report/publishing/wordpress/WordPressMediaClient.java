package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class WordPressMediaClient {

  private static final Pattern VALID_WP_PDF_URL =
      Pattern.compile(
          "^https://tuleva\\.ee/wp-content/uploads/\\d{4}/\\d{2}/[A-Za-z0-9._-]+\\.pdf$");

  private final RestClient restClient;

  public String upload(String filename, byte[] pdfBytes) {
    var sanitizedFilename = sanitizeFilename(filename);
    log.info(
        "Uploading PDF to WordPress: filename={}, size={}bytes",
        sanitizedFilename,
        pdfBytes.length);

    @SuppressWarnings("unchecked")
    var response =
        restClient
            .post()
            .uri("/media")
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=\"" + sanitizedFilename + "\"")
            .body(pdfBytes)
            .retrieve()
            .body(Map.class);

    var sourceUrl = (String) response.get("source_url");
    if (sourceUrl == null || !VALID_WP_PDF_URL.matcher(sourceUrl).matches()) {
      throw new IllegalStateException(
          "WordPress returned invalid source_url: " + truncate(String.valueOf(sourceUrl), 200));
    }

    log.info("WordPress upload successful: sourceUrl={}", sourceUrl);
    return sourceUrl;
  }

  static String sanitizeFilename(String filename) {
    return filename.replaceAll("[\\\\\"\\r\\n]", "_");
  }

  private static String truncate(String s, int maxLen) {
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
  }
}
