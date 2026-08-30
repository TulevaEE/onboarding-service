package ee.tuleva.onboarding.mandate;

import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record MandateProcessResult(List<MandateProcessOutcome> outcomes) {

  public record MandateProcessOutcome(
      String processId, boolean successful, @Nullable Integer errorCode) {}
}
