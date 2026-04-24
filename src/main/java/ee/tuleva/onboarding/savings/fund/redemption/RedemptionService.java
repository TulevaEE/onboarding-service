package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionRequestedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionService {

  private static final int FUND_UNITS_SCALE = 5;
  private static final BigDecimal MAX_WITHDRAWAL_TOLERANCE = new BigDecimal("0.01");

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final LedgerService ledgerService;
  private final SavingsFundLedger savingsFundLedger;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final FundNavProvider navProvider;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final SavingFundDeadlinesService deadlinesService;
  private final Clock clock;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public RedemptionRequest createRedemptionRequest(
      AuthenticatedPerson authenticatedPerson,
      BigDecimal amount,
      Currency currency,
      String customerIban) {
    if (currency != EUR) {
      throw new IllegalArgumentException("Only EUR currency is supported: currency=" + currency);
    }

    validateAmountPrecision(amount);

    PartyId partyId = authenticatedPerson.toPartyId();
    validateOnboarding(partyId);
    validateIbanBelongsToParty(customerIban, partyId);

    BigDecimal nav = navProvider.getDisplayNav(TKF100);
    BigDecimal availableUnits = getEffectiveAvailableFundUnits(partyId);
    BigDecimal fundUnits = convertAmountToFundUnits(amount, nav, availableUnits);

    validateFundUnits(fundUnits, availableUnits);

    RedemptionRequest request =
        RedemptionRequest.builder()
            .userId(authenticatedPerson.getUserId())
            .partyId(partyId)
            .fundUnits(fundUnits)
            .requestedAmount(amount)
            .customerIban(customerIban)
            .status(RESERVED)
            .build();

    RedemptionRequest saved = redemptionRequestRepository.save(request);

    savingsFundLedger.reserveFundUnitsForRedemption(partyId, fundUnits, saved.getId());
    log.info(
        "Created redemption request: id={}, userId={}, party={}, requestedAmount={}, fundUnits={}, nav={}, customerIban={}",
        saved.getId(),
        authenticatedPerson.getUserId(),
        partyId,
        amount,
        fundUnits,
        nav,
        customerIban);

    applicationEventPublisher.publishEvent(
        new RedemptionRequestedEvent(
            saved.getId(), authenticatedPerson.getUserId(), partyId, amount, fundUnits));

    return saved;
  }

  private BigDecimal convertAmountToFundUnits(
      BigDecimal amount, BigDecimal nav, BigDecimal availableUnits) {
    BigDecimal fundUnits = amount.divide(nav, FUND_UNITS_SCALE, HALF_UP);
    BigDecimal maxWithdrawalValue = availableUnits.multiply(nav);
    BigDecimal difference = amount.subtract(maxWithdrawalValue).abs();

    if (difference.compareTo(MAX_WITHDRAWAL_TOLERANCE) <= 0) {
      log.info(
          "Max withdrawal detected: requestedAmount={}, maxValue={}, roundingToAllUnits={}",
          amount,
          maxWithdrawalValue,
          availableUnits);
      return availableUnits;
    }

    return fundUnits;
  }

  public List<RedemptionRequest> getPendingRedemptionsForUser(Long userId) {
    return redemptionRequestRepository.findByUserIdAndStatusIn(
        userId, List.of(RESERVED, IN_REVIEW, VERIFIED));
  }

  public RedemptionRequest getRedemption(UUID id) {
    return redemptionRequestRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Redemption not found: id=" + id));
  }

  @Transactional
  public void cancelRedemption(UUID id, AuthenticatedPerson authenticatedPerson) {
    RedemptionRequest request = getRedemption(id);
    PartyId partyId = authenticatedPerson.toPartyId();

    if (!request.getPartyId().equals(partyId)) {
      throw new IllegalArgumentException(
          "Redemption does not belong to party: id=" + id + ", party=" + partyId);
    }

    validateCancellationDeadline(request);

    savingsFundLedger.cancelRedemptionReservation(partyId, request.getFundUnits(), request.getId());
    redemptionStatusService.changeStatus(id, CANCELLED);
    log.info(
        "Cancelled redemption request: id={}, userId={}, party={}",
        id,
        authenticatedPerson.getUserId(),
        partyId);
  }

  private void validateCancellationDeadline(RedemptionRequest request) {
    Instant deadline = deadlinesService.getCancellationDeadline(request);
    Instant now = Instant.now(clock);

    if (now.isAfter(deadline)) {
      throw new IllegalStateException(
          "Cancellation deadline has passed: id="
              + request.getId()
              + ", deadline="
              + deadline
              + ", now="
              + now);
    }
  }

  private void validateOnboarding(PartyId partyId) {
    if (!savingsFundOnboardingService.isOnboardingCompleted(partyId)) {
      throw new IllegalStateException("Savings fund onboarding not completed: party=" + partyId);
    }
  }

  private void validateFundUnits(BigDecimal fundUnits, BigDecimal effectiveAvailable) {
    if (fundUnits.compareTo(effectiveAvailable) > 0) {
      throw new IllegalArgumentException(
          "Insufficient fund units: requested=" + fundUnits + ", available=" + effectiveAvailable);
    }

    if (fundUnits.compareTo(ZERO) <= 0) {
      throw new IllegalArgumentException("Fund units must be positive: " + fundUnits);
    }
  }

  private BigDecimal getEffectiveAvailableFundUnits(PartyId partyId) {
    return ledgerService
        .getPartyAccount(partyId.code(), LedgerParty.PartyType.from(partyId.type()), FUND_UNITS)
        .getBalance()
        .negate();
  }

  private void validateIbanBelongsToParty(String iban, PartyId partyId) {
    List<String> ibans = savingFundPaymentRepository.findDepositBankAccountIbans(partyId);
    if (!ibans.contains(iban)) {
      throw new IllegalArgumentException(
          "IBAN does not belong to party: iban=" + iban + ", party=" + partyId);
    }
  }

  private void validateAmountPrecision(BigDecimal amount) {
    if (amount.scale() > 2) {
      throw new IllegalArgumentException(
          "Amount cannot have more than 2 decimal places: amount=" + amount);
    }
  }
}
