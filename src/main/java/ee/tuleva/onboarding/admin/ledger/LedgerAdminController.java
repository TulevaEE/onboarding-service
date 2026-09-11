package ee.tuleva.onboarding.admin.ledger;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
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
@Profile("!staging")
public class LedgerAdminController {

  private final AdminTokenValidator tokenValidator;
  private final SavingsFundLedger savingsFundLedger;
  private final NavFeeAccrualLedger navFeeAccrualLedger;

  @Transactional
  @PostMapping("/adjustments")
  public List<Map<String, String>> createAdjustments(
      @RequestHeader("X-Admin-Token") String token, @RequestBody List<AdjustmentRequest> requests) {

    tokenValidator.validate(token);

    log.info("Admin triggered adjustments: count={}", requests.size());

    var results =
        requests.stream()
            .map(
                request -> {
                  var transaction =
                      savingsFundLedger.recordAdjustment(
                          request.debitAccount(),
                          request.debitParty(),
                          request.creditAccount(),
                          request.creditParty(),
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

  @Transactional
  @PostMapping("/reclassifications")
  public List<Map<String, String>> createReclassifications(
      @RequestHeader("X-Admin-Token") String token,
      @RequestBody List<ReclassificationRequest> requests) {

    tokenValidator.validateWithOpsAccess(token);
    if (requests.stream().anyMatch(request -> request.description().isBlank())) {
      throw new ResponseStatusException(BAD_REQUEST, "A description is required");
    }

    log.info("Admin triggered reclassifications: count={}", requests.size());

    var transactions =
        savingsFundLedger.reclassifyBetweenParties(
            requests.stream().map(ReclassificationRequest::toReclassification).toList());
    transactions.forEach(
        transaction ->
            log.info(
                "Reclassification recorded: transactionId={}, account={}, externalReference={}, correctedTransactionId={}",
                transaction.getId(),
                transaction.getMetadata().get("account"),
                transaction.getExternalReference(),
                transaction.getMetadata().get("correctedTransactionId")));
    return transactions.stream()
        .map(transaction -> Map.of("transactionId", transaction.getId().toString()))
        .toList();
  }

  @PostMapping("/blackrock-adjustment")
  public BlackrockAdjustmentResult recordBlackrockAdjustment(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String fundCode,
      @RequestParam BigDecimal amount,
      @RequestParam @DateTimeFormat(iso = DATE) LocalDate date) {

    tokenValidator.validateWithOpsAccess(token);

    TulevaFund fund = TulevaFund.fromCode(fundCode);
    BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_UP);
    log.info(
        "Admin triggered BlackRock adjustment: fund={}, date={}, targetBalance={}",
        fund,
        date,
        roundedAmount);

    return navFeeAccrualLedger.recordBlackrockAdjustment(fund, date, roundedAmount);
  }
}
