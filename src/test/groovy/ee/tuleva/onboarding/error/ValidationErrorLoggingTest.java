package ee.tuleva.onboarding.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ValidationErrorLoggingTest {

  private static final String REJECTED_VALUE = "55 667 788";

  private final MockMvc mvc =
      standaloneSetup(new ValidatingController())
          .setControllerAdvice(new ErrorHandlingControllerAdvice())
          .build();

  private final Logger errorLogger = (Logger) LoggerFactory.getLogger("ee.tuleva.onboarding.error");
  private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
  private Level previousLevel;

  @BeforeEach
  void captureLogs() {
    previousLevel = errorLogger.getLevel();
    errorLogger.setLevel(Level.DEBUG);
    logAppender.start();
    errorLogger.addAppender(logAppender);
  }

  @AfterEach
  void releaseLogs() {
    errorLogger.detachAppender(logAppender);
    errorLogger.setLevel(previousLevel);
  }

  @Test
  void anInvalidRequestBodyIsLoggedByObjectFieldAndCodeWithoutTheRejectedValue() throws Exception {
    mvc.perform(
            post("/test/validated-body")
                .contentType(APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + REJECTED_VALUE + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("Pattern"))
        .andExpect(jsonPath("$.errors[0].path").value("phoneNumber"));

    assertThat(loggedMessages()).noneMatch(message -> message.contains(REJECTED_VALUE));
    assertThat(loggedMessages())
        .anyMatch(message -> message.contains("field=phoneNumber") && message.contains("Pattern"));
  }

  @Test
  void validationErrorsRaisedByAControllerAreLoggedWithoutTheRejectedValue() throws Exception {
    mvc.perform(
            post("/test/manually-validated-body")
                .contentType(APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + REJECTED_VALUE + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("Pattern"));

    assertThat(loggedMessages()).noneMatch(message -> message.contains(REJECTED_VALUE));
    assertThat(loggedMessages())
        .anyMatch(message -> message.contains("field=phoneNumber") && message.contains("Pattern"));
  }

  private java.util.List<String> loggedMessages() {
    return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  record PhoneBody(@Pattern(regexp = "^\\d+$") String phoneNumber) {}

  @RestController
  static class ValidatingController {

    @PostMapping("/test/validated-body")
    void validated(@Valid @RequestBody PhoneBody body) {}

    @PostMapping("/test/manually-validated-body")
    void manuallyValidated(@Valid @RequestBody PhoneBody body, Errors errors) {
      if (errors.hasErrors()) {
        throw new ValidationErrorsException(errors);
      }
    }
  }
}
