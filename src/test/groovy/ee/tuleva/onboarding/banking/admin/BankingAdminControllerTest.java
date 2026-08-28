package ee.tuleva.onboarding.banking.admin;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.seb.processor.SuspenseReclassificationService;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BankingAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class BankingAdminControllerTest {

  private static final List<BankAccount> SAVINGS_FUND_BANK_ACCOUNTS =
      List.of(
          new BankAccount("EE001234567890123456", BankAccountType.DEPOSIT_EUR, TKF100, "gw-test"),
          new BankAccount(
              "EE001234567890123457", BankAccountType.WITHDRAWAL_EUR, TKF100, "gw-test"),
          new BankAccount(
              "EE001234567890123458", BankAccountType.FUND_INVESTMENT_EUR, TKF100, "gw-test"));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationEventPublisher eventPublisher;
  @MockitoBean private BankAccounts bankAccounts;
  @MockitoBean private SuspenseReclassificationService suspenseReclassificationService;

  @Test
  void fetchSebHistory_withValidToken_returnsOk() throws Exception {
    given(bankAccounts.findAll(TKF100)).willReturn(SAVINGS_FUND_BANK_ACCOUNTS);

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
    given(bankAccounts.findAll(TKF100)).willReturn(SAVINGS_FUND_BANK_ACCOUNTS);

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
}
