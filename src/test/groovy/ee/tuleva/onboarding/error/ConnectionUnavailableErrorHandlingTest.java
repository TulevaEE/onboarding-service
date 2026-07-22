package ee.tuleva.onboarding.error;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ConnectionUnavailableErrorHandlingTest {

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ErrorHandlingControllerAdvice())
            .build();
  }

  @Test
  void jdbcConnectionAcquisitionTimeoutReturns503WithRetryAfter() throws Exception {
    mvc.perform(get("/test/jdbc-connection-timeout"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"));
  }

  @Test
  void transactionConnectionAcquisitionTimeoutReturns503WithRetryAfter() throws Exception {
    mvc.perform(get("/test/transaction-connection-timeout"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"));
  }

  @Test
  void unrelatedDataAccessErrorIsNotMappedTo503() {
    assertThatThrownBy(() -> mvc.perform(get("/test/data-integrity-violation")))
        .hasRootCauseInstanceOf(DataIntegrityViolationException.class);
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/test/jdbc-connection-timeout")
    void jdbcConnectionTimeout() {
      throw new CannotGetJdbcConnectionException(
          "Failed to obtain JDBC Connection",
          new SQLTransientConnectionException(
              "HikariPool-1 - Connection is not available, request timed out after 30000ms"));
    }

    @GetMapping("/test/transaction-connection-timeout")
    void transactionConnectionTimeout() {
      throw new CannotCreateTransactionException(
          "Could not open JPA EntityManager for transaction",
          new SQLTransientConnectionException(
              "HikariPool-1 - Connection is not available, request timed out after 30000ms"));
    }

    @GetMapping("/test/data-integrity-violation")
    void dataIntegrityViolation() {
      throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
    }
  }
}
