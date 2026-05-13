package ee.tuleva.onboarding.investment.report.publishing.gmail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
public class GmailDraftClient {

  private final RestClient restClient;

  public record PdfAttachment(String filename, byte[] content) {}

  @SuppressWarnings("unchecked")
  public String createDraft(
      String to, String cc, String subject, String htmlBody, List<PdfAttachment> attachments) {
    log.info(
        "Creating Gmail draft: to={}, cc={}, subject={}, attachments={}",
        to,
        cc,
        subject,
        attachments.size());

    var rawMessage = buildMimeMessage(to, cc, subject, htmlBody, attachments);
    var encodedMessage =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8));

    var response =
        restClient
            .post()
            .uri("/users/me/drafts")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("message", Map.of("raw", encodedMessage)))
            .retrieve()
            .body(Map.class);

    var draftId = (String) response.get("id");
    log.info("Gmail draft created: draftId={}", draftId);
    return draftId;
  }

  @SuppressWarnings("unchecked")
  public String fetchSignature() {
    try {
      var response = restClient.get().uri("/users/me/settings/sendAs").retrieve().body(Map.class);

      var sendAs = (List<Map<String, Object>>) response.get("sendAs");
      if (sendAs == null || sendAs.isEmpty()) {
        return defaultSignature();
      }

      return sendAs.stream()
          .filter(s -> Boolean.TRUE.equals(s.get("isDefault")))
          .findFirst()
          .or(() -> sendAs.stream().findFirst())
          .map(s -> (String) s.get("signature"))
          .filter(s -> s != null && !s.isBlank())
          .orElse(defaultSignature());
    } catch (Exception e) {
      log.warn("Failed to fetch Gmail signature, using default: {}", e.getMessage());
      return defaultSignature();
    }
  }

  private static String defaultSignature() {
    return "Lugupidamisega,<br>Süsteem";
  }

  static String buildMimeMessage(
      String to, String cc, String subject, String htmlBody, List<PdfAttachment> attachments) {
    var boundary = "boundary_" + UUID.randomUUID().toString().replace("-", "");
    var sb = new StringBuilder();

    sb.append("MIME-Version: 1.0\r\n");
    sb.append("To: ").append(to).append("\r\n");
    if (cc != null && !cc.isBlank()) {
      sb.append("Cc: ").append(cc).append("\r\n");
    }
    sb.append("Subject: =?UTF-8?B?")
        .append(Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)))
        .append("?=\r\n");
    sb.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n");
    sb.append("\r\n");

    sb.append("--").append(boundary).append("\r\n");
    sb.append("Content-Type: text/html; charset=UTF-8\r\n");
    sb.append("Content-Transfer-Encoding: base64\r\n");
    sb.append("\r\n");
    sb.append(
        Base64.getMimeEncoder(76, "\r\n".getBytes())
            .encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8)));
    sb.append("\r\n");

    for (var attachment : attachments) {
      sb.append("--").append(boundary).append("\r\n");
      sb.append("Content-Type: application/pdf; name=\"")
          .append(attachment.filename())
          .append("\"\r\n");
      sb.append("Content-Disposition: attachment; filename=\"")
          .append(attachment.filename())
          .append("\"\r\n");
      sb.append("Content-Transfer-Encoding: base64\r\n");
      sb.append("\r\n");
      sb.append(Base64.getMimeEncoder(76, "\r\n".getBytes()).encodeToString(attachment.content()));
      sb.append("\r\n");
    }

    sb.append("--").append(boundary).append("--\r\n");
    return sb.toString();
  }
}
