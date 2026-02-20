package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RedemptionController.class)
@AutoConfigureMockMvc
class RedemptionControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private RedemptionService redemptionService;

  private final AuthenticatedPerson authPerson = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  @DisplayName("POST /v1/savings/redemptions creates redemption and returns 201")
  void createRedemption_createsAndReturnsCreated() throws Exception {
    var requestId = UUID.randomUUID();
    var redemptionRequest =
        redemptionRequestFixture()
            .id(requestId)
            .userId(authPerson.getUserId())
            .customerIban("EE471000001020145685")
            .build();

    when(redemptionService.createRedemptionRequest(
            eq(authPerson.getUserId()),
            eq(new BigDecimal("10.00")),
            eq(EUR),
            eq("EE471000001020145685")))
        .thenReturn(redemptionRequest);

    String requestBody =
        """
        {
          "amount": 10.00,
          "currency": "EUR",
          "iban": "EE471000001020145685"
        }
        """;

    mvc.perform(
            post("/v1/savings/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(requestId.toString()))
        .andExpect(jsonPath("$.status").value("RESERVED"));

    verify(redemptionService)
        .createRedemptionRequest(
            eq(authPerson.getUserId()),
            eq(new BigDecimal("10.00")),
            eq(EUR),
            eq("EE471000001020145685"));
  }

  @Test
  @DisplayName("POST /v1/savings/redemptions with missing amount returns 400")
  void createRedemption_withMissingAmount_returnsBadRequest() throws Exception {
    String requestBody =
        """
        {
          "currency": "EUR",
          "iban": "EE471000001020145685"
        }
        """;

    mvc.perform(
            post("/v1/savings/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /v1/savings/redemptions with negative amount returns 400")
  void createRedemption_withNegativeAmount_returnsBadRequest() throws Exception {
    String requestBody =
        """
        {
          "amount": -10.00,
          "currency": "EUR",
          "iban": "EE471000001020145685"
        }
        """;

    mvc.perform(
            post("/v1/savings/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /v1/savings/redemptions with missing IBAN returns 400")
  void createRedemption_withMissingIban_returnsBadRequest() throws Exception {
    String requestBody =
        """
        {
          "amount": 10.00,
          "currency": "EUR"
        }
        """;

    mvc.perform(
            post("/v1/savings/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("DELETE /v1/savings/redemptions/{id} cancels redemption")
  void cancelRedemption_cancelsAndReturnsNoContent() throws Exception {
    var requestId = UUID.randomUUID();

    mvc.perform(
            delete("/v1/savings/redemptions/{id}", requestId)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isNoContent());

    verify(redemptionService).cancelRedemption(requestId, authPerson.getUserId());
  }
}
