package ee.tuleva.onboarding.admin;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@TestPropertySource(properties = "admin.api-token=valid-token")
@WithMockUser
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationEventPublisher eventPublisher;
  @MockitoBean private SavingsFundLedger savingsFundLedger;
  @MockitoBean private NavCalculationService navCalculationService;
  @MockitoBean private NavPublisher navPublisher;
  @MockitoBean private Clock clock;

  @Test
  void fetchSebHistory_withValidToken_returnsOk() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2026-01-01")))
        .andExpect(content().string(containsString("2026-01-31")));
  }

  @Test
  void fetchSebHistory_withAccountParam_fetchesOnlyThatAccount() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31")
                .param("account", "FUND_INVESTMENT_EUR"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("FUND_INVESTMENT_EUR")))
        .andExpect(content().string(not(containsString("DEPOSIT_EUR"))))
        .andExpect(content().string(not(containsString("WITHDRAWAL_EUR"))));
  }

  @Test
  void fetchSebHistory_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("from", "2026-01-01")
                .param("to", "2026-01-31"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void fetchSebHistory_withMissingToken_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .param("from", "2026-01-01")
                .param("to", "2026-01-31"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createAdjustments_withValidToken_returnsTransactionIds() throws Exception {
    var transactionId = UUID.randomUUID();
    var transaction = LedgerTransaction.builder().id(transactionId).build();
    when(savingsFundLedger.recordAdjustment(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(transaction);

    mockMvc
        .perform(
            post("/admin/adjustments")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    [
                      {
                        "debitAccount": "CASH_RESERVED",
                        "debitPersonalCode": "39107050268",
                        "creditAccount": "CASH",
                        "creditPersonalCode": "39107050268",
                        "amount": 1.01,
                        "description": "Reverse duplicate"
                      },
                      {
                        "debitAccount": "CASH_RESERVED",
                        "debitPersonalCode": "48709090311",
                        "creditAccount": "CASH",
                        "creditPersonalCode": "48709090311",
                        "amount": 500.00,
                        "description": "Reverse duplicate"
                      }
                    ]
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));

    verify(savingsFundLedger, times(2))
        .recordAdjustment(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void createAdjustments_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/adjustments")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void calculateNav_withValidToken_calculatesAndPublishes() throws Exception {
    var result = sampleNavResult(LocalDate.of(2026, 2, 17));
    when(navCalculationService.calculate(TKF100, LocalDate.of(2026, 2, 17))).thenReturn(result);

    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("date", "2026-02-17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.navPerUnit").value(1.0))
        .andExpect(jsonPath("$.aum").value(1000000));

    verify(navPublisher).publish(result);
  }

  @Test
  void calculateNav_withPublishFalse_calculatesButDoesNotPublish() throws Exception {
    var result = sampleNavResult(LocalDate.of(2026, 2, 17));
    when(navCalculationService.calculate(TKF100, LocalDate.of(2026, 2, 17))).thenReturn(result);

    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("date", "2026-02-17")
                .param("publish", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.navPerUnit").value(1.0));

    verify(navPublisher, never()).publish(any());
  }

  @Test
  void calculateNav_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/calculate-nav")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("date", "2026-02-17"))
        .andExpect(status().isUnauthorized());
  }

  private NavCalculationResult sampleNavResult(LocalDate date) {
    return NavCalculationResult.builder()
        .fund(TKF100)
        .calculationDate(date)
        .securitiesValue(new BigDecimal("800000"))
        .cashPosition(new BigDecimal("200000"))
        .receivables(BigDecimal.ZERO)
        .pendingSubscriptions(BigDecimal.ZERO)
        .pendingRedemptions(BigDecimal.ZERO)
        .managementFeeAccrual(BigDecimal.ZERO)
        .depotFeeAccrual(BigDecimal.ZERO)
        .payables(BigDecimal.ZERO)
        .blackrockAdjustment(BigDecimal.ZERO)
        .aum(new BigDecimal("1000000"))
        .unitsOutstanding(new BigDecimal("1000000"))
        .navPerUnit(BigDecimal.ONE)
        .positionReportDate(date)
        .priceDate(date)
        .calculatedAt(Instant.parse("2026-02-17T15:30:00Z"))
        .componentDetails(Map.of())
        .build();
  }
}
