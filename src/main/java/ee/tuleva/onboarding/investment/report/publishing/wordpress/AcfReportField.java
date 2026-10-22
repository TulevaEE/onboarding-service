package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static java.util.stream.StreamSupport.stream;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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
    return attachmentIdsIn(storedValue).anyMatch(id -> id == attachmentId);
  }

  Optional<Integer> attachmentId() {
    var attachmentIds = attachmentIdsIn(storedValue).distinct().toList();
    if (attachmentIds.size() == 1) {
      return Optional.of(attachmentIds.getFirst());
    }
    if (attachmentIds.isEmpty() && isEmpty(storedValue)) {
      return Optional.empty();
    }
    throw new IllegalStateException(
        "Report field holds no single attachment id: field=" + NAME + ", value=" + storedValue);
  }

  private static boolean isEmpty(@Nullable Object stored) {
    return switch (stored) {
      case null -> true;
      case Boolean set -> !set;
      case CharSequence text -> text.toString().isBlank();
      case Iterable<?> attachments -> !attachments.iterator().hasNext();
      default -> false;
    };
  }

  private static Stream<Integer> attachmentIdsIn(@Nullable Object stored) {
    return switch (stored) {
      case Number number -> wholeNumber(number.toString());
      case CharSequence text -> wholeNumber(text.toString());
      case Map<?, ?> attachment ->
          ATTACHMENT_ID_KEYS.stream().flatMap(key -> attachmentIdsIn(attachment.get(key)));
      case Iterable<?> attachments ->
          stream(attachments.spliterator(), false).flatMap(AcfReportField::attachmentIdsIn);
      case null, default -> Stream.empty();
    };
  }

  private static Stream<Integer> wholeNumber(String value) {
    try {
      return Stream.of(new BigDecimal(value.trim()).intValueExact());
    } catch (NumberFormatException | ArithmeticException notAWholeNumber) {
      return Stream.empty();
    }
  }
}
