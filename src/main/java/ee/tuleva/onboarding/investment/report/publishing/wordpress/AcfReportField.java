package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static java.util.stream.StreamSupport.stream;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record AcfReportField(@Nullable Object storedValue) {

  private static final String NAME = "investment_report_file";
  private static final List<String> ATTACHMENT_ID_KEYS = List.of("ID", "id");

  static Map<String, Object> pageUpdate(int attachmentId) {
    return Map.of("acf", Map.of(NAME, attachmentId));
  }

  static AcfReportField storedIn(@Nullable Map<String, Object> pageResponse) {
    if (pageResponse == null || !(pageResponse.get("acf") instanceof Map<?, ?> acf)) {
      return new AcfReportField(null);
    }
    return new AcfReportField(acf.get(NAME));
  }

  boolean holds(int attachmentId) {
    return isAttachmentId(storedValue, attachmentId);
  }

  private static boolean isAttachmentId(@Nullable Object stored, int attachmentId) {
    return switch (stored) {
      case null -> false;
      case Number number -> isSameNumber(number.toString(), attachmentId);
      case CharSequence text -> isSameNumber(text.toString(), attachmentId);
      case Map<?, ?> attachment -> holdsAttachmentId(attachment, attachmentId);
      case Iterable<?> attachments -> holdsAttachmentId(attachments, attachmentId);
      default -> false;
    };
  }

  private static boolean holdsAttachmentId(Map<?, ?> attachment, int attachmentId) {
    return ATTACHMENT_ID_KEYS.stream()
        .anyMatch(key -> isAttachmentId(attachment.get(key), attachmentId));
  }

  private static boolean holdsAttachmentId(Iterable<?> attachments, int attachmentId) {
    return stream(attachments.spliterator(), false)
        .anyMatch(attachment -> isAttachmentId(attachment, attachmentId));
  }

  private static boolean isSameNumber(String value, int attachmentId) {
    try {
      return new BigDecimal(value.trim()).compareTo(BigDecimal.valueOf(attachmentId)) == 0;
    } catch (NumberFormatException notANumber) {
      return false;
    }
  }
}
