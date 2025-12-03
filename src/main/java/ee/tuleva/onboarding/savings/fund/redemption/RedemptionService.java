package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PENDING;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedemptionService {

  private static final int FUND_UNITS_SCALE = 5;
  private static final BigDecimal MAX_WITHDRAWAL_TOLERANCE = new BigDecimal("0.00001");

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final LedgerService ledgerService;
  private final UserService userService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final SavingsFundNavProvider navProvider;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Transactional
  public RedemptionRequest createRedemptionRequest(
      Long userId, BigDecimal amount, Currency currency, String customerIban) {
    if (currency != EUR) {
      throw new IllegalArgumentException("Only EUR currency is supported: currency=" + currency);
    }

    User user = userService.getByIdOrThrow(userId);
    validateOnboarding(user);
    validateIbanBelongsToUser(customerIban, userId);

    BigDecimal nav = navProvider.getCurrentNav();
    BigDecimal availableUnits = getEffectiveAvailableFundUnits(user);
    BigDecimal fundUnits = convertAmountToFundUnits(amount, nav, availableUnits);

    validateFundUnits(fundUnits, availableUnits);

    RedemptionRequest request =
        RedemptionRequest.builder()
            .userId(userId)
            .fundUnits(fundUnits)
            .customerIban(customerIban)
            .status(PENDING)
            .build();

    RedemptionRequest saved = redemptionRequestRepository.save(request);
    log.info(
        "Created redemption request: id={}, userId={}, amount={}, fundUnits={}, nav={}, customerIban={}",
        saved.getId(),
        userId,
        amount,
        fundUnits,
        nav,
        customerIban);

    return saved;
  }

  private BigDecimal convertAmountToFundUnits(
      BigDecimal amount, BigDecimal nav, BigDecimal availableUnits) {
    BigDecimal fundUnits = amount.divide(nav, FUND_UNITS_SCALE, HALF_UP);

    BigDecimal maxWithdrawalValue = availableUnits.multiply(nav);
    BigDecimal difference = amount.subtract(maxWithdrawalValue).abs();

    if (difference.compareTo(MAX_WITHDRAWAL_TOLERANCE) <= 0) {
      log.info(
          "Max withdrawal detected: requested amount={}, max value={}, rounding to all units={}",
          amount,
          maxWithdrawalValue,
          availableUnits);
      return availableUnits;
    }

    if (fundUnits.subtract(availableUnits).abs().compareTo(MAX_WITHDRAWAL_TOLERANCE) <= 0) {
      log.info(
          "Max withdrawal detected (units): calculated units={}, available={}, rounding to all",
          fundUnits,
          availableUnits);
      return availableUnits;
    }

    return fundUnits;
  }

  public List<RedemptionRequest> getUserRedemptions(Long userId) {
    return redemptionRequestRepository.findByUserIdOrderByRequestedAtDesc(userId);
  }

  public RedemptionRequest getRedemption(UUID id) {
    return redemptionRequestRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Redemption not found: id=" + id));
  }

  @Transactional
  public void cancelRedemption(UUID id, Long userId) {
    RedemptionRequest request = getRedemption(id);

    if (!request.getUserId().equals(userId)) {
      throw new IllegalArgumentException("Redemption does not belong to user: id=" + id);
    }

    redemptionStatusService.cancel(id);
    log.info("Cancelled redemption request: id={}, userId={}", id, userId);
  }

  private void validateOnboarding(User user) {
    if (!savingsFundOnboardingService.isOnboardingCompleted(user)) {
      throw new IllegalStateException(
          "User savings fund onboarding not completed: userId=" + user.getId());
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

  private BigDecimal getEffectiveAvailableFundUnits(User user) {
    BigDecimal availableUnits = getAvailableFundUnits(user);
    BigDecimal pendingRedemptionUnits = getPendingRedemptionUnits(user.getId());
    return availableUnits.subtract(pendingRedemptionUnits);
  }

  private BigDecimal getAvailableFundUnits(User user) {
    return ledgerService.getUserAccount(user, FUND_UNITS).getBalance().negate();
  }

  private BigDecimal getPendingRedemptionUnits(Long userId) {
    return redemptionRequestRepository.findByUserIdAndStatus(userId, PENDING).stream()
        .map(RedemptionRequest::getFundUnits)
        .reduce(ZERO, BigDecimal::add);
  }

  private void validateIbanBelongsToUser(String iban, Long userId) {
    List<String> userIbans = savingFundPaymentRepository.findUserDepositBankAccountIbans(userId);
    if (!userIbans.contains(iban)) {
      throw new IllegalArgumentException(
          "IBAN does not belong to user: iban=" + iban + ", userId=" + userId);
    }
  }
}
