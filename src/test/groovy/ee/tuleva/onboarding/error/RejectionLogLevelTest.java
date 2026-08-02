package ee.tuleva.onboarding.error;

import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ee.tuleva.onboarding.auth.smartid.SmartIdSessionNotFoundException;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class RejectionLogLevelTest {

  private final ErrorHandlingControllerAdvice advice = new ErrorHandlingControllerAdvice();
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(ErrorHandlingControllerAdvice.class);

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "smart.id.account.not.found",
        "smart.id.user.refused",
        "mobile.id.cancelled",
        "mobile.id.timeout",
        "mobile.id.certificates.revoked",
        "new.user.flow.signup.error.email.duplicate"
      })
  void logsRejectionsCausedByTheUserBelowErrorLevel(String errorCode) {
    advice.handleErrors(new ErrorsResponseException(ofSingleError(errorCode, "message")));

    assertThat(loggedLevels()).containsExactly(Level.INFO);
  }

  @Test
  void keepsUnexpectedRejectionsAtErrorLevel() {
    advice.handleErrors(
        new ErrorsResponseException(ofSingleError("epis.message.exception", "boom")));

    assertThat(loggedLevels()).containsExactly(Level.ERROR);
  }

  @Test
  void keepsRejectionsWithoutAnErrorCodeAtErrorLevel() {
    advice.handleErrors(new ErrorsResponseException(new ErrorsResponse(List.of())));

    assertThat(loggedLevels()).containsExactly(Level.ERROR);
  }

  @Test
  void logsAMissingAuthSessionBelowErrorLevel() {
    advice.handleAuthSessionNotFound(new SmartIdSessionNotFoundException());

    assertThat(loggedLevels()).containsExactly(Level.INFO);
  }

  private List<Level> loggedLevels() {
    return appender.list.stream().map(ILoggingEvent::getLevel).toList();
  }
}
