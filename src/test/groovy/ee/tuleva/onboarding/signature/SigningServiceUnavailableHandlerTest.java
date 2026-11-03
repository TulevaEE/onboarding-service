package ee.tuleva.onboarding.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.logback.SentryAppender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.digidoc4j.exceptions.OCSPRequestFailedException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

class SigningServiceUnavailableHandlerTest {

  private final SigningServiceUnavailableHandler handler = new SigningServiceUnavailableHandler();

  private final SigningServiceUnavailableException unavailable =
      new SigningServiceUnavailableException(new OCSPRequestFailedException("S-1"));

  @Test
  void asksTheSignerToRetryLaterWithAServiceUnavailableResponse() {
    var response = handler.handleSigningServiceUnavailable(unavailable);

    assertThat(response)
        .isEqualTo(
            ResponseEntity.status(SERVICE_UNAVAILABLE)
                .header(RETRY_AFTER, "60")
                .body(
                    ErrorsResponse.ofSingleError(
                        "signature.service.unavailable",
                        "Signing is temporarily unavailable, please retry later.")));
  }

  @Test
  void sendsSentryOneErrorUnderItsOwnFingerprintTaggedWithTheFailingCause() {
    List<SentryEvent> sentEvents = new CopyOnWriteArrayList<>();
    Sentry.init(
        options -> {
          options.setDsn("https://public@sentry.example.com/1");
          options.setBeforeSend(
              (event, hint) -> {
                sentEvents.add(event);
                return null;
              });
        });
    var appender = sentryAppenderOn(handlerLogger());
    try {
      handler.handleSigningServiceUnavailable(unavailable);
    } finally {
      handlerLogger().detachAppender(appender);
      appender.stop();
      Sentry.close();
    }

    assertThat(sentEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(SentryLevel.ERROR);
              assertThat(event.getFingerprints())
                  .isEqualTo(List.of("signing-trust-validation-unavailable"));
              assertThat(event.getTags())
                  .containsEntry("component", "digidoc4j-tsl")
                  .containsEntry("cause", "OCSPRequestFailedException");
            });
  }

  private static Logger handlerLogger() {
    return (Logger) LoggerFactory.getLogger(SigningServiceUnavailableHandler.class);
  }

  private static SentryAppender sentryAppenderOn(Logger logger) {
    var appender = new SentryAppender();
    appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    logger.addAppender(appender);
    return appender;
  }
}
