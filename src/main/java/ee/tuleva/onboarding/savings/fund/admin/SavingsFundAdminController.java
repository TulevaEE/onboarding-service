package ee.tuleva.onboarding.savings.fund.admin;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistEntry;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.UnattributedPaymentAttributionService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationService;
import ee.tuleva.onboarding.savings.fund.nav.NavPublisher;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionBatchJob;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionReviewService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
public class SavingsFundAdminController {

  private final AdminTokenValidator tokenValidator;
  private final NavCalculationService navCalculationService;
  private final NavPublisher navPublisher;
  private final RedemptionBatchJob redemptionBatchJob;
  private final RedemptionReviewService redemptionReviewService;
  private final IbanWhitelistService ibanWhitelistService;
  private final UnattributedPaymentAttributionService unattributedPaymentAttributionService;
  private final Clock clock;

  @PostMapping("/calculate-nav")
  public NavCalculationResult calculateNav(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(defaultValue = "TKF100") String fundCode,
      @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DATE) LocalDate date,
      @RequestParam(defaultValue = "false") boolean publish) {

    tokenValidator.validateWithOpsAccess(token);

    LocalDate calculationDate = date != null ? date : LocalDate.now(clock);

    log.info(
        "Admin triggered NAV calculation: fund={}, date={}, publish={}",
        fundCode,
        calculationDate,
        publish);

    NavCalculationResult result = navCalculationService.calculate(fundCode, calculationDate);

    if (publish) {
      navPublisher.publish(result);
      log.info("NAV published: date={}, navPerUnit={}", calculationDate, result.navPerUnit());
    }

    return result;
  }

  @PostMapping("/redemptions/{id}/retry-payout")
  public String retryRedemptionPayout(
      @RequestHeader("X-Admin-Token") String token, @PathVariable UUID id) {

    tokenValidator.validate(token);

    log.info("Admin triggered redemption payout retry: id={}", id);
    redemptionBatchJob.retryFailedPayout(id);

    return "Retried redemption payout for " + id;
  }

  @PostMapping("/redemptions/{id}/approve-review")
  public String approveRedemptionReview(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable UUID id,
      @RequestParam String approvedBy,
      @RequestParam String reason) {

    tokenValidator.validateWithOpsAccess(token);
    if (approvedBy.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "approvedBy is required");
    }
    if (reason.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "A reason is required");
    }

    log.info("Admin approving redemption review: id={}, approvedBy={}", id, approvedBy);
    redemptionReviewService.approve(id, approvedBy, reason);

    return "Approved redemption review: id=" + id;
  }

  @PostMapping("/whitelist-iban")
  public String whitelistIban(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam PartyId.Type partyType,
      @RequestParam String partyCode,
      @RequestParam String iban,
      @RequestParam(required = false) @Nullable String comment) {

    tokenValidator.validateWithOpsAccess(token);
    if (!IbanValidator.isValid(iban)) {
      throw new ResponseStatusException(BAD_REQUEST, "Invalid IBAN: iban=" + iban);
    }
    ibanWhitelistService.add(new PartyId(partyType, partyCode), iban, comment);

    return "Whitelisted IBAN: partyType="
        + partyType
        + ", partyCode="
        + partyCode
        + ", iban="
        + iban;
  }

  @GetMapping("/whitelist-iban")
  public List<IbanWhitelistEntry> listWhitelistedIbans(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam(required = false) PartyId.@Nullable Type partyType,
      @RequestParam(required = false) @Nullable String partyCode) {

    tokenValidator.validateWithOpsAccess(token);
    if ((partyType == null) != (partyCode == null)) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Provide both partyType and partyCode, or neither");
    }
    PartyId partyId =
        (partyType != null && partyCode != null) ? new PartyId(partyType, partyCode) : null;

    return ibanWhitelistService.list(partyId);
  }

  @DeleteMapping("/whitelist-iban")
  public String removeWhitelistedIban(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam PartyId.Type partyType,
      @RequestParam String partyCode,
      @RequestParam String iban) {

    tokenValidator.validateWithOpsAccess(token);
    ibanWhitelistService.remove(new PartyId(partyType, partyCode), iban);

    return "Removed whitelisted IBAN: partyType="
        + partyType
        + ", partyCode="
        + partyCode
        + ", iban="
        + iban;
  }

  @PostMapping("/savings-fund/payments/{paymentId}/attribute")
  public Map<String, String> attributeUnattributedPayment(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable UUID paymentId,
      @RequestParam PartyId.Type partyType,
      @RequestParam String partyCode,
      @RequestParam(defaultValue = "false") boolean returnCancelled) {

    tokenValidator.validateWithOpsAccess(token);

    log.info(
        "Admin triggered manual payment attribution: paymentId={}, partyType={}, partyCode={}, returnCancelled={}",
        paymentId,
        partyType,
        partyCode,
        returnCancelled);

    var payment =
        unattributedPaymentAttributionService.attribute(
            paymentId, new PartyId(partyType, partyCode), returnCancelled);

    return Map.of(
        "paymentId", payment.getId().toString(),
        "status", payment.getStatus().name(),
        "partyType", partyType.name(),
        "partyCode", partyCode);
  }
}
