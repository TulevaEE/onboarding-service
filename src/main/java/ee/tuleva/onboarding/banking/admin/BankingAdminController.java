package ee.tuleva.onboarding.banking.admin;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.banking.seb.processor.SuspenseReclassificationService;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class BankingAdminController {

  private final AdminTokenValidator tokenValidator;
  private final BankAccounts bankAccounts;
  private final ApplicationEventPublisher eventPublisher;
  private final SuspenseReclassificationService suspenseReclassificationService;

  @PostMapping("/fetch-seb-history")
  public String fetchSebHistory(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate to,
      @RequestParam(required = false) @Nullable BankAccountType account,
      @RequestParam(required = false) @Nullable String fundCode) {

    tokenValidator.validate(token);

    var fund = fundCode != null ? parseFundCode(fundCode) : TKF100;
    var accounts =
        bankAccounts.findAll(fund).stream()
            .filter(bankAccount -> account == null || bankAccount.type() == account)
            .toList();

    if (accounts.isEmpty()) {
      throw new ResponseStatusException(
          BAD_REQUEST, "No bank accounts match: fund=%s, account=%s".formatted(fund, account));
    }

    log.info("Admin triggered SEB history fetch: from={}, to={}, accounts={}", from, to, accounts);

    for (BankAccount bankAccount : accounts) {
      log.info("Fetching SEB history: account={}", bankAccount);
      eventPublisher.publishEvent(new FetchSebHistoricTransactionsRequested(bankAccount, from, to));
    }

    return "Fetched SEB history for " + accounts + " from " + from + " to " + to;
  }

  @PostMapping("/reclassify-suspense")
  public Map<String, Object> reclassifySuspense(
      @RequestHeader("X-Admin-Token") String token, @RequestParam String fundCode) {

    tokenValidator.validate(token);

    var fund = parseFundCode(fundCode);
    log.info("Admin triggered suspense reclassification: fund={}", fund);

    var result = suspenseReclassificationService.reclassify(fund);

    return Map.of(
        "fund", fund.name(),
        "reclassified", result.reclassified(),
        "remaining", result.remaining());
  }

  private static TulevaFund parseFundCode(String fundCode) {
    try {
      return TulevaFund.fromCode(fundCode);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(BAD_REQUEST, e.getMessage());
    }
  }
}
