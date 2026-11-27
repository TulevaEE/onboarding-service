package ee.tuleva.onboarding.banking.admin;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.banking.seb.processor.SuspenseReclassificationService;
import ee.tuleva.onboarding.banking.seb.processor.UnderlyingFundFeeMoveService;
import ee.tuleva.onboarding.banking.seb.processor.UnderlyingFundFeeMoveService.UnderlyingFundFeeMovePlan;
import ee.tuleva.onboarding.ledger.BookedManagementFeePayment;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankingAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
@RecordApplicationEvents
class BankingAdminControllerTest {

  private static final List<BankAccount> SAVINGS_FUND_BANK_ACCOUNTS =
      List.of(
          new BankAccount("EE001234567890123456", BankAccountType.DEPOSIT_EUR, TKF100, "gw-test"),
          new BankAccount(
              "EE001234567890123457", BankAccountType.WITHDRAWAL_EUR, TKF100, "gw-test"),
          new BankAccount(
              "EE001234567890123458", BankAccountType.FUND_INVESTMENT_EUR, TKF100, "gw-test"));

  private static final List<BankAccount> ALL_BANK_ACCOUNTS =
      List.of(
          SAVINGS_FUND_BANK_ACCOUNTS.get(0),
          SAVINGS_FUND_BANK_ACCOUNTS.get(1),
          SAVINGS_FUND_BANK_ACCOUNTS.get(2),
          new BankAccount(
              "EE001234567890123475",
              BankAccountType.FUND_INVESTMENT_EUR,
              TulevaFund.TUK75,
              "gw-test"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEvents applicationEvents;

  @MockitoBean private BankAccounts bankAccounts;
  @MockitoBean private SuspenseReclassificationService suspenseReclassificationService;
  @MockitoBean private UnderlyingFundFeeMoveService underlyingFundFeeMoveService;

  @Test
  void fetchSebHistory_withoutFundCode_fetchesEveryFundsAccounts() throws Exception {
    given(bankAccounts.findAll()).willReturn(ALL_BANK_ACCOUNTS);

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

    var expectedEvents =
        ALL_BANK_ACCOUNTS.stream()
            .map(
                account ->
                    new FetchSebHistoricTransactionsRequested(
                        account, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .toList();
    assertThat(applicationEvents.stream(FetchSebHistoricTransactionsRequested.class))
        .containsExactlyInAnyOrderElementsOf(expectedEvents);
  }

  @Test
  void fetchSebHistory_withAccountParam_fetchesOnlyThatAccount() throws Exception {
    given(bankAccounts.findAll()).willReturn(ALL_BANK_ACCOUNTS);

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
  void fetchSebHistory_withFundCode_fetchesThatFundsAccounts() throws Exception {
    given(bankAccounts.findAll(TulevaFund.TUK75))
        .willReturn(
            List.of(
                new BankAccount(
                    "EE001234567890123475",
                    BankAccountType.FUND_INVESTMENT_EUR,
                    TulevaFund.TUK75,
                    "gw-test")));

    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-02-01")
                .param("to", "2026-02-28")
                .param("fundCode", "TUK75"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("TUK75")));
  }

  @Test
  void fetchSebHistory_withAccountTypeMissingForFund_returnsBadRequest() throws Exception {
    given(bankAccounts.findAll(TulevaFund.TUK75))
        .willReturn(
            List.of(
                new BankAccount(
                    "EE001234567890123475",
                    BankAccountType.FUND_INVESTMENT_EUR,
                    TulevaFund.TUK75,
                    "gw-test")));

    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-02-01")
                .param("to", "2026-02-28")
                .param("fundCode", "TUK75")
                .param("account", "DEPOSIT_EUR"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void fetchSebHistory_withUnknownFundCode_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-02-01")
                .param("to", "2026-02-28")
                .param("fundCode", "TUK7S"))
        .andExpect(status().isBadRequest());
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
  void fetchSebHistory_withTokenDifferingInLastCharacter_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/fetch-seb-history")
                .with(csrf())
                .header("X-Admin-Token", "valid-tokeX")
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
  void reclassifySuspense_withUnknownFundCode_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/reclassify-suspense")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK7S"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reclassifySuspense_withValidToken_returnsCounts() throws Exception {
    given(suspenseReclassificationService.reclassify(TulevaFund.TUK75))
        .willReturn(new SuspenseReclassificationService.ReclassificationResult(3, 1));

    mockMvc
        .perform(
            post("/admin/reclassify-suspense")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fund").value("TUK75"))
        .andExpect(jsonPath("$.reclassified").value(3))
        .andExpect(jsonPath("$.remaining").value(1));
  }

  @Test
  void reclassifySuspense_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/reclassify-suspense")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TUK75"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void previewUnderlyingFundFeeMoves_returnsThePaymentsWithCountAndSum_andMovesNothing()
      throws Exception {
    var aprilPayment =
        new BookedManagementFeePayment(
            TulevaFund.TUK75,
            UUID.fromString("00000000-0000-0000-0000-000000000415"),
            Instant.parse("2026-04-15T20:59:59.999999Z"),
            new BigDecimal("50.00"),
            "BR tasud TUK75");
    given(underlyingFundFeeMoveService.preview(TulevaFund.TUK75))
        .willReturn(new UnderlyingFundFeeMovePlan(TulevaFund.TUK75, List.of(aprilPayment), 0));

    mockMvc
        .perform(
            post("/admin/underlying-fund-fee-moves/preview")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fund").value("TUK75"))
        .andExpect(jsonPath("$.count").value(1))
        .andExpect(jsonPath("$.sum").value(50.0))
        .andExpect(jsonPath("$.alreadyMoved").value(0))
        .andExpect(
            jsonPath("$.payments[0].externalReference")
                .value("00000000-0000-0000-0000-000000000415"))
        .andExpect(jsonPath("$.payments[0].bookingDate").value("2026-04-15"))
        .andExpect(jsonPath("$.payments[0].amount").value(50.0));
    verify(underlyingFundFeeMoveService, never()).move(TulevaFund.TUK75);
  }

  @Test
  void moveUnderlyingFundFees_returnsWhatItMovedAndWhatWasMovedBefore() throws Exception {
    given(underlyingFundFeeMoveService.move(TulevaFund.TUK75))
        .willReturn(new UnderlyingFundFeeMovePlan(TulevaFund.TUK75, List.of(), 2));

    mockMvc
        .perform(
            post("/admin/underlying-fund-fee-moves")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("fundCode", "TUK75"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fund").value("TUK75"))
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.sum").value(0))
        .andExpect(jsonPath("$.alreadyMoved").value(2));
  }

  @Test
  void underlyingFundFeeMoves_withInvalidToken_returnsUnauthorizedAndMovesNothing()
      throws Exception {
    mockMvc
        .perform(
            post("/admin/underlying-fund-fee-moves")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .param("fundCode", "TUK75"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(underlyingFundFeeMoveService);
  }
}
