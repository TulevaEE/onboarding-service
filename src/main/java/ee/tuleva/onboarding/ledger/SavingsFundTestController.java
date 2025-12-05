package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessageReceiver;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher;
import ee.tuleva.onboarding.swedbank.statement.BankAccountType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/savings-fund-test")
@AllArgsConstructor
@Profile("!staging")
public class SavingsFundTestController {

  private final UserService userService;
  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final SwedbankStatementFetcher swedbankStatementFetcher;
  private final SwedbankMessageReceiver swedbankMessageReceiver;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final RedemptionRequestRepository redemptionRequestRepository;

  @Operation(summary = "Get my ledger accounts")
  @GetMapping("/account")
  public List<LedgerAccount> getAccounts(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId).orElseThrow();

    LedgerParty ledgerParty =
        ledgerPartyService
            .getParty(user)
            .orElseThrow(() -> new RuntimeException("No ledger party found for user " + userId));

    return ledgerAccountService.getAccounts(ledgerParty);
  }

  @Operation(summary = "Trigger statement request")
  @PostMapping("/swedbank/statement")
  public void sendSwedbankRequest() {
    swedbankStatementFetcher.sendRequest(DEPOSIT_EUR);
  }

  @Operation(summary = "Trigger statement request")
  @PostMapping("/swedbank/statement/historic")
  public void sendSwedbankRequest(
      @RequestParam(value = "account") BankAccountType account,
      @RequestParam(value = "fromDate") @DateTimeFormat(pattern = "YYYY-MM-dd") LocalDate fromDate,
      @RequestParam(value = "toDate") @DateTimeFormat(pattern = "YYYY-MM-dd") LocalDate toDate) {
    swedbankStatementFetcher.sendHistoricRequest(account, fromDate, toDate);
  }

  @Operation(summary = "Trigger message getter")
  @GetMapping("/swedbank/statement")
  public void getSwedbankResponse() {
    swedbankMessageReceiver.getResponses();
  }

  @Operation(summary = "Backdate user VERIFIED deposits by 2 days")
  @GetMapping("/backdate-deposits")
  public int backdateDeposits(@AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    if (!savingsFundOnboardingService.isUserWhitelisted(authenticatedPerson.getUserId())) {
      throw new IllegalStateException("User not whitelisted");
    }
    return savingFundPaymentRepository.TEST_backdateVerifiedPayments(
        authenticatedPerson.getUserId());
  }

  @Operation(summary = "Backdate user VERIFIED redemption requests by 1 day")
  @GetMapping("/backdate-redemptions")
  public int backdateRedemptions(@AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    if (!savingsFundOnboardingService.isUserWhitelisted(authenticatedPerson.getUserId())) {
      throw new IllegalStateException("User not whitelisted");
    }
    return redemptionRequestRepository.TEST_backdateVerifiedRequests(
        authenticatedPerson.getUserId());
  }
}
