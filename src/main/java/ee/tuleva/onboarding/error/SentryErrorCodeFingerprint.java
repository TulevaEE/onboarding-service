package ee.tuleva.onboarding.error;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions.BeforeSendCallback;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class SentryErrorCodeFingerprint implements BeforeSendCallback {

  public static final String ERROR_CODE = "error_code";

  private static final String SENTRY_DEFAULT_GROUPING = "{{ default }}";

  @Override
  public @Nullable SentryEvent execute(SentryEvent event, Hint hint) {
    String errorCode = errorCodeOf(event);
    if (errorCode != null) {
      event.setTag(ERROR_CODE, errorCode);
      event.setFingerprints(List.of(SENTRY_DEFAULT_GROUPING, errorCode));
    }
    return event;
  }

  private static @Nullable String errorCodeOf(SentryEvent event) {
    String tag = event.getTag(ERROR_CODE);
    return tag != null ? tag : MDC.get(ERROR_CODE);
  }
}
