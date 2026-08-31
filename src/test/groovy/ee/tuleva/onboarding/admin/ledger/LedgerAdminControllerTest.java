package ee.tuleva.onboarding.admin.ledger;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LedgerAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class LedgerAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SavingsFundLedger savingsFundLedger;
  @MockitoBean private NavFeeAccrualLedger navFeeAccrualLedger;

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
                        "debitPartyCode": "39107050268",
                        "debitPartyType": "PERSON",
                        "creditAccount": "CASH",
                        "creditPartyCode": "39107050268",
                        "creditPartyType": "PERSON",
                        "amount": 1.01,
                        "description": "Reverse duplicate"
                      },
                      {
                        "debitAccount": "CASH_RESERVED",
                        "debitPartyCode": "48709090311",
                        "debitPartyType": "PERSON",
                        "creditAccount": "CASH",
                        "creditPartyCode": "48709090311",
                        "creditPartyType": "PERSON",
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
  void createAdjustments_withDynamicAccountTypes_returnsTransactionIds() throws Exception {
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
                        "debitAccount": "TRADE_UNIT_SETTLEMENT:TKF100:LU1291102447",
                        "creditAccount": "SECURITIES_CUSTODY:TKF100:LU1291102447",
                        "amount": 11704,
                        "description": "Trade unit backfill"
                      }
                    ]
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));
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
  void recordBlackrockAdjustment_withOpsToken_returnsOk() throws Exception {
    var result =
        new BlackrockAdjustmentResult(
            TulevaFund.TUK75,
            LocalDate.of(2026, 4, 2),
            BigDecimal.ZERO,
            new BigDecimal("38531.70"),
            new BigDecimal("38531.70"),
            true);
    given(
            navFeeAccrualLedger.recordBlackrockAdjustment(
                TulevaFund.TUK75, LocalDate.of(2026, 4, 2), new BigDecimal("38531.70")))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("fundCode", "TUK75")
                .param("amount", "38531.70")
                .param("date", "2026-04-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fund").value("TUK75"))
        .andExpect(jsonPath("$.delta").value(38531.70))
        .andExpect(jsonPath("$.transactionCreated").value(true));
  }

  @Test
  void recordBlackrockAdjustment_withAdminToken_returnsOk() throws Exception {
    var result =
        new BlackrockAdjustmentResult(
            TulevaFund.TUK75,
            LocalDate.of(2026, 4, 2),
            BigDecimal.ZERO,
            new BigDecimal("38531.70"),
            new BigDecimal("38531.70"),
            true);
    given(
            navFeeAccrualLedger.recordBlackrockAdjustment(
                TulevaFund.TUK75, LocalDate.of(2026, 4, 2), new BigDecimal("38531.70")))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75")
                .param("amount", "38531.70")
                .param("date", "2026-04-02"))
        .andExpect(status().isOk());
  }

  @Test
  void recordBlackrockAdjustment_roundsAmountToTwoDecimalPlaces() throws Exception {
    var result =
        new BlackrockAdjustmentResult(
            TulevaFund.TUK75,
            LocalDate.of(2026, 4, 2),
            BigDecimal.ZERO,
            new BigDecimal("56980.96"),
            new BigDecimal("56980.96"),
            true);
    given(
            navFeeAccrualLedger.recordBlackrockAdjustment(
                TulevaFund.TUK75, LocalDate.of(2026, 4, 2), new BigDecimal("56980.96")))
        .willReturn(result);

    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .param("fundCode", "TUK75")
                .param("amount", "56980.95999999999")
                .param("date", "2026-04-02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetBalance").value(56980.96));
  }

  @Test
  void recordBlackrockAdjustment_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/blackrock-adjustment")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TUK75")
                .param("amount", "38531.70")
                .param("date", "2026-04-02"))
        .andExpect(status().isUnauthorized());
  }
}
