package ee.tuleva.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

class OnboardingServiceApplicationTest {

  private static final String ORIGINAL_FILE_ENCODING = System.getProperty("file.encoding");

  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(OnboardingServiceApplication.class);

  @AfterEach
  void restoreFileEncodingAndDetachAppender() {
    System.setProperty("file.encoding", ORIGINAL_FILE_ENCODING);
    logger.detachAppender(logAppender);
  }

  @Test
  void logsAnErrorWhenFileEncodingIsNotUtf8() {
    System.setProperty("file.encoding", "ISO-8859-1");
    logAppender.start();
    logger.addAppender(logAppender);

    runMainWithMockedSpringApplication();

    assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.ERROR).hasSize(1);
  }

  @Test
  void doesNotLogWhenFileEncodingIsUtf8() {
    System.setProperty("file.encoding", "UTF-8");
    logAppender.start();
    logger.addAppender(logAppender);

    runMainWithMockedSpringApplication();

    assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
  }

  private void runMainWithMockedSpringApplication() {
    try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
      springApplication
          .when(
              () ->
                  SpringApplication.run(
                      eq(OnboardingServiceApplication.class), any(String[].class)))
          .thenReturn(null);

      OnboardingServiceApplication.main(new String[0]);
    }
  }
}
