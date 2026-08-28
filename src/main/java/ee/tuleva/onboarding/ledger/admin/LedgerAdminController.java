package ee.tuleva.onboarding.ledger.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.BlackrockAdjustmentResult;
import ee.tuleva.onboarding.ledger.NavFeeAccrualLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
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
