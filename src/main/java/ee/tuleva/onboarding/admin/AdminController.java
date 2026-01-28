package ee.tuleva.onboarding.admin;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
public class AdminController {

  private final ApplicationEventPublisher eventPublisher;

  @Value("${admin.api-token:}")
  private String adminApiToken;

  @PostMapping("/fetch-seb-history")
  public String fetchSebHistory(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    validateToken(token);

    log.info("Admin triggered SEB history fetch: from={}, to={}", from, to);

    for (BankAccountType account : BankAccountType.values()) {
      log.info("Fetching SEB history: account={}", account);
      eventPublisher.publishEvent(new FetchSebHistoricTransactionsRequested(account, from, to));
    }

    return "Fetched SEB history for all accounts from " + from + " to " + to;
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
