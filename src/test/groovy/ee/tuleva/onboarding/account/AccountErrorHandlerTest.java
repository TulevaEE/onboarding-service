package ee.tuleva.onboarding.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.error.ErrorHandlingControllerAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AccountErrorHandlerTest {

  @RestController
  static class ThrowingController {
    @GetMapping("/account-statement-failure")
    String explode() {
      throw new PensionRegistryAccountStatementConnectionException();
    }
  }

  @Test
  void mapsRegistryConnectionFailureToGatewayTimeoutAheadOfTheGenericAdvice() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ErrorHandlingControllerAdvice(), new AccountErrorHandler())
            .build();

    mockMvc.perform(get("/account-statement-failure")).andExpect(status().isGatewayTimeout());
  }
}
