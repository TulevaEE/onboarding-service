package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

  private final ApplicationEventPublisher eventPublisher;
  private final SavingsFundLedger savingsFundLedger;

  @Value("${admin.api-token:}")
  private String adminApiToken;

  @PostMapping("/fetch-seb-history")
  public String fetchSebHistory(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) BankAccountType account) {

    validateToken(token);

    var accounts = account != null ? List.of(account) : Arrays.asList(BankAccountType.values());

    log.info("Admin triggered SEB history fetch: from={}, to={}, accounts={}", from, to, accounts);

    for (BankAccountType bankAccount : accounts) {
      log.info("Fetching SEB history: account={}", bankAccount);
      eventPublisher.publishEvent(new FetchSebHistoricTransactionsRequested(bankAccount, from, to));
    }

    return "Fetched SEB history for " + accounts + " from " + from + " to " + to;
  }

  @Transactional
  @PostMapping("/adjustments")
  public List<Map<String, String>> createAdjustments(
      @RequestHeader("X-Admin-Token") String token, @RequestBody List<AdjustmentRequest> requests) {

    validateToken(token);

    log.info("Admin triggered adjustments: count={}", requests.size());

    var results =
        requests.stream()
            .map(
                request -> {
                  var transaction =
                      savingsFundLedger.recordAdjustment(
                          request.debitAccount(),
                          request.debitPersonalCode(),
                          request.creditAccount(),
                          request.creditPersonalCode(),
                          request.amount(),
                          request.externalReference(),
                          request.description());
                  log.info(
                      "Adjustment recorded: transactionId={}, debitAccount={}, creditAccount={}, amount={}, description={}",
                      transaction.getId(),
                      request.debitAccount(),
                      request.creditAccount(),
                      request.amount(),
                      request.description());
                  return Map.of("transactionId", transaction.getId().toString());
                })
            .toList();

    log.info("All adjustments completed: count={}", results.size());
    return results;
  }

  private void validateToken(String token) {
    if (adminApiToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin API not configured");
    }
    if (!adminApiToken.equals(token)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
    }
  }
}
