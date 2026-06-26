package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Getter
public class HistoricalImportFormatException extends RuntimeException {

  private final List<String> missingHeaders;
  private final List<String> requiredHeaders;

  public HistoricalImportFormatException(
      List<String> missingHeaders, List<String> requiredHeaders) {
    super(
        "Missing required CSV headers: missing=%s, required=%s"
            .formatted(missingHeaders, requiredHeaders));
    this.missingHeaders = List.copyOf(missingHeaders);
    this.requiredHeaders = List.copyOf(requiredHeaders);
  }
}
