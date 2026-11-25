package ee.tuleva.onboarding.admin.ledger;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.PartyReclassification;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
    when(savingsFundLedger.recordAdjustment(any(), any(), any(), any(), any(), any(), any(), any()))
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
        .recordAdjustment(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void createAdjustments_withDynamicAccountTypes_returnsTransactionIds() throws Exception {
    var transactionId = UUID.randomUUID();
    var transaction = LedgerTransaction.builder().id(transactionId).build();
    when(savingsFundLedger.recordAdjustment(any(), any(), any(), any(), any(), any(), any(), any()))
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
  void createAdjustments_withOpsToken_recordsAnEntryBetweenOwnClearingAccountsAndSaysWhoRecordedIt()
      throws Exception {
    var transactionId = UUID.randomUUID();
    var reference = UUID.fromString("0198f1d6-4c8a-7c3e-9b21-6f0b0d2a1c55");
    given(
            savingsFundLedger.recordAdjustment(
                "PAYOUTS_CASH_CLEARING",
                null,
                "UNRECONCILED_PAYOUT_ACCOUNT_RECEIPTS",
                null,
                new BigDecimal("10.00"),
                reference,
                "Payment received on the payout account by mistake",
                "ops-token"))
        .willReturn(LedgerTransaction.builder().id(transactionId).build());

    mockMvc
        .perform(
            adjustments(
                "ops-token",
                """
                [{"debitAccount": "PAYOUTS_CASH_CLEARING",
                  "creditAccount": "UNRECONCILED_PAYOUT_ACCOUNT_RECEIPTS",
                  "amount": 10.00,
                  "externalReference": "0198f1d6-4c8a-7c3e-9b21-6f0b0d2a1c55",
                  "description": "Payment received on the payout account by mistake"}]
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));
  }

  @Test
  void createAdjustments_withOpsToken_refusesAPartyAccountOrOneOffTheList() throws Exception {
    mockMvc
        .perform(
            adjustments(
                "ops-token",
                """
                [{"debitAccount": "CASH_RESERVED", "debitPartyCode": "38888888888",
                  "debitPartyType": "PERSON", "creditAccount": "CASH",
                  "creditPartyCode": "38888888888", "creditPartyType": "PERSON",
                  "amount": 1.00, "description": "Move a holder's cash"}]
                """))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            adjustments(
                "ops-token",
                """
                [{"debitAccount": "FUND_UNITS_OUTSTANDING", "creditAccount": "FUND_UNITS_EQUITY",
                  "amount": 1.00, "description": "Mint units"}]
                """))
        .andExpect(status().isForbidden());

    verifyNoInteractions(savingsFundLedger);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"debitAccount\": \"PAYOUTS_CASH_CLEARING\", \"creditAccount\": \"NAV_EQUITY\"",
        "\"debitAccount\": \"PAYOUTS_CASH_CLEARING:TKF100\","
            + " \"creditAccount\": \"UNRECONCILED_BANK_RECEIPTS\"",
        "\"debitAccount\": \"payouts_cash_clearing\","
            + " \"creditAccount\": \"UNRECONCILED_BANK_RECEIPTS\"",
        "\"debitAccount\": \"PAYOUTS_CASH_CLEARING\", \"creditAccount\": \"CASH\","
            + " \"creditPartyCode\": \"38888888888\", \"creditPartyType\": \"PERSON\""
      })
  void createAdjustments_withOpsToken_refusesEitherSideOffTheListOrNamingAParty(String accounts)
      throws Exception {
    mockMvc
        .perform(
            adjustments(
                "ops-token",
                "[{" + accounts + ", \"amount\": 1.00, \"description\": \"Off the list\"}]"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void createAdjustments_withOpsToken_writesNothingWhenOneLegOfTheBatchIsRefused()
      throws Exception {
    mockMvc
        .perform(
            adjustments(
                "ops-token",
                """
                [{"debitAccount": "PAYOUTS_CASH_CLEARING",
                  "creditAccount": "UNRECONCILED_BANK_RECEIPTS",
                  "amount": 1.00, "description": "Allowed"},
                 {"debitAccount": "FUND_UNITS_OUTSTANDING", "creditAccount": "FUND_UNITS_EQUITY",
                  "amount": 1.00, "description": "Refused"}]
                """))
        .andExpect(status().isForbidden());

    verifyNoInteractions(savingsFundLedger);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"amount\": 0, \"description\": \"Zero\"",
        "\"description\": \"No amount\"",
        "\"amount\": 5.00"
      })
  void createAdjustments_refusesAZeroOrMissingAmountOrAMissingDescription(String fields)
      throws Exception {
    mockMvc
        .perform(
            adjustments(
                "valid-token",
                "[{\"debitAccount\": \"PAYOUTS_CASH_CLEARING\","
                    + " \"creditAccount\": \"UNRECONCILED_BANK_RECEIPTS\", "
                    + fields
                    + "}]"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void createAdjustments_refusesANonPositiveAmountOrAMissingDescriptionForAnyToken()
      throws Exception {
    mockMvc
        .perform(
            adjustments(
                "valid-token",
                """
                [{"debitAccount": "PAYOUTS_CASH_CLEARING",
                  "creditAccount": "UNRECONCILED_BANK_RECEIPTS",
                  "amount": -5.00, "description": "Negative"}]
                """))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            adjustments(
                "valid-token",
                """
                [{"debitAccount": "PAYOUTS_CASH_CLEARING",
                  "creditAccount": "UNRECONCILED_BANK_RECEIPTS",
                  "amount": 5.00, "description": " "}]
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void createAdjustments_answersAnAccountTheLedgerDoesNotKnowAsABadRequest() throws Exception {
    given(
            savingsFundLedger.recordAdjustment(
                any(), any(), any(), any(), any(), any(), any(), any()))
        .willThrow(new IllegalArgumentException("No such account: NOT_AN_ACCOUNT"));

    mockMvc
        .perform(
            adjustments(
                "valid-token",
                """
                [{"debitAccount": "NOT_AN_ACCOUNT",
                  "creditAccount": "UNRECONCILED_BANK_RECEIPTS",
                  "amount": 5.00, "description": "Typo"}]
                """))
        .andExpect(status().isBadRequest());
  }

  private static MockHttpServletRequestBuilder adjustments(String token, String body) {
    return post("/admin/adjustments")
        .with(csrf())
        .header("X-Admin-Token", token)
        .contentType(APPLICATION_JSON)
        .content(body);
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

  @Test
  void createReclassifications_movesTheBalanceBetweenTwoParties() throws Exception {
    var transactionId = UUID.randomUUID();
    var redemptionId = UUID.randomUUID();
    var mispostedPayoutId = UUID.randomUUID();
    when(savingsFundLedger.reclassifyBetweenParties(any()))
        .thenReturn(
            List.of(
                LedgerTransaction.builder()
                    .id(transactionId)
                    .externalReference(redemptionId)
                    .metadata(Map.of("account", "CASH_REDEMPTION"))
                    .build()));

    mockMvc
        .perform(
            post("/admin/reclassifications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    [
                      {
                        "account": "CASH_REDEMPTION",
                        "debitPartyCode": "12345678",
                        "debitPartyType": "LEGAL_ENTITY",
                        "creditPartyCode": "39107050268",
                        "creditPartyType": "PERSON",
                        "amount": 2991.94,
                        "externalReference": "%s",
                        "correctedTransactionId": "%s",
                        "description": "Payout was booked to the representative"
                      }
                    ]
                    """
                        .formatted(redemptionId, mispostedPayoutId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].transactionId").value(transactionId.toString()));

    verify(savingsFundLedger)
        .reclassifyBetweenParties(
            List.of(
                new PartyReclassification(
                    UserAccount.CASH_REDEMPTION,
                    new PartyRef(LedgerParty.PartyType.LEGAL_ENTITY, "12345678"),
                    new PartyRef(LedgerParty.PartyType.PERSON, "39107050268"),
                    new BigDecimal("2991.94"),
                    redemptionId,
                    mispostedPayoutId,
                    "Payout was booked to the representative")));
  }

  @Test
  void createReclassifications_withBlankDescription_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/reclassifications")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    [
                      {
                        "account": "CASH_REDEMPTION",
                        "debitPartyCode": "12345678",
                        "debitPartyType": "LEGAL_ENTITY",
                        "creditPartyCode": "39107050268",
                        "creditPartyType": "PERSON",
                        "amount": 2991.94,
                        "description": " "
                      }
                    ]
                    """))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void createReclassifications_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/reclassifications")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isUnauthorized());
  }
}
