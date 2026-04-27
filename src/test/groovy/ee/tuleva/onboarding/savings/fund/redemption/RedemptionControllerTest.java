package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

  private final AuthenticatedPerson legalEntityAuthPerson =
      sampleAuthenticatedPersonLegalEntity().build();
  private final Authentication legalEntityAuthentication =
      new UsernamePasswordAuthenticationToken(
          legalEntityAuthPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void createRedemption_createsAndReturnsCreated() throws Exception {
    var requestId = UUID.randomUUID();
    var redemptionRequest =
        redemptionRequestFixture()
            .id(requestId)
            .userId(authPerson.getUserId())
            .partyId(authPerson.toPartyId())
            .customerIban("EE471000001020145685")
            .build();

    when(redemptionService.createRedemptionRequest(
            eq(authPerson), eq(new BigDecimal("10.00")), eq(EUR), eq("EE471000001020145685")))
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
            eq(authPerson), eq(new BigDecimal("10.00")), eq(EUR), eq("EE471000001020145685"));
  }

  @Test
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
  void createRedemption_withLegalEntityRole_succeedsWhenAuthorized() throws Exception {
    var requestId = UUID.randomUUID();
    var redemptionRequest =
        redemptionRequestFixture()
            .id(requestId)
            .userId(legalEntityAuthPerson.getUserId())
            .partyId(legalEntityAuthPerson.toPartyId())
            .customerIban("EE471000001020145685")
            .build();

    when(redemptionService.createRedemptionRequest(
            eq(legalEntityAuthPerson),
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
                .with(authentication(legalEntityAuthentication)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(requestId.toString()));
  }

  @Test
  void createRedemption_whenServiceDeniesAccess_returnsForbidden() throws Exception {
    when(redemptionService.createRedemptionRequest(
            eq(legalEntityAuthPerson),
            eq(new BigDecimal("10.00")),
            eq(EUR),
            eq("EE471000001020145685")))
        .thenThrow(new AccessDeniedException("Not a board member"));

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
                .with(authentication(legalEntityAuthentication)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cancelRedemption_cancelsAndReturnsNoContent() throws Exception {
    var requestId = UUID.randomUUID();

    mvc.perform(
            delete("/v1/savings/redemptions/{id}", requestId)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isNoContent());

    verify(redemptionService).cancelRedemption(requestId, authPerson);
  }

  @Test
  void cancelRedemption_withLegalEntityRole_succeedsWhenAuthorized() throws Exception {
    var requestId = UUID.randomUUID();

    mvc.perform(
            delete("/v1/savings/redemptions/{id}", requestId)
                .with(csrf())
                .with(authentication(legalEntityAuthentication)))
        .andExpect(status().isNoContent());

    verify(redemptionService).cancelRedemption(requestId, legalEntityAuthPerson);
  }

  @Test
  void cancelRedemption_whenServiceDeniesAccess_returnsForbidden() throws Exception {
    var requestId = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new AccessDeniedException("Not a board member"))
        .when(redemptionService)
        .cancelRedemption(requestId, legalEntityAuthPerson);

    mvc.perform(
            delete("/v1/savings/redemptions/{id}", requestId)
                .with(csrf())
                .with(authentication(legalEntityAuthentication)))
        .andExpect(status().isForbidden());
  }
}
