package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static java.util.stream.StreamSupport.stream;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

record AcfReportField(@Nullable Object storedValue) {

  private static final String NAME = "investment_report_file";
  private static final List<String> ATTACHMENT_ID_KEYS = List.of("ID", "id");
  private static final Pattern WHOLE_NUMBER = Pattern.compile("\\s*\\d+\\s*");

  static Map<String, Object> pageUpdate(int attachmentId) {
    return Map.of("acf", Map.of(NAME, attachmentId));
  }

  static AcfReportField storedIn(@Nullable Map<String, Object> pageResponse) {
    if (pageResponse == null || !(pageResponse.get("acf") instanceof Map<?, ?> acf)) {
      return new AcfReportField(null);
    }
    return new AcfReportField(acf.get(NAME));
  }

  static AcfReportField readFrom(Map<String, Object> page) {
    if (!(page.get("acf") instanceof Map<?, ?> acf) || !acf.containsKey(NAME)) {
      throw new IllegalStateException(
          "WordPress page does not expose the report field over REST: field="
              + NAME
              + ", pageId="
              + page.get("id"));
    }
    return new AcfReportField(acf.get(NAME));
  }

  boolean holds(int attachmentId) {
    return isAttachmentId(storedValue, attachmentId);
  }

  Optional<Integer> attachmentId() {
    if (isEmpty(storedValue)) {
      return Optional.empty();
    }
    return Optional.of(
        attachmentIdIn(storedValue)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Report field holds no attachment id: field="
                            + NAME
                            + ", value="
                            + storedValue)));
  }

  private static boolean isEmpty(@Nullable Object stored) {
    return switch (stored) {
      case null -> true;
      case Boolean set -> !set;
      case CharSequence text -> text.toString().isBlank();
      default -> false;
    };
  }

  private static Optional<Integer> attachmentIdIn(@Nullable Object stored) {
    return switch (stored) {
      case Number number -> Optional.of(number.intValue());
      case CharSequence text when WHOLE_NUMBER.matcher(text).matches() ->
          Optional.of(Integer.valueOf(text.toString().trim()));
      case Map<?, ?> attachment ->
          ATTACHMENT_ID_KEYS.stream()
              .flatMap(key -> attachmentIdIn(attachment.get(key)).stream())
              .findFirst();
      case null, default -> Optional.empty();
    };
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
