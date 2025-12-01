package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.PENDING;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
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

  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final LedgerService ledgerService;
  private final UserService userService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;

  @Transactional
  public RedemptionRequest createRedemptionRequest(
      Long userId, BigDecimal fundUnits, String customerIban) {
    User user = userService.getByIdOrThrow(userId);
    validateRequest(user, fundUnits);

    RedemptionRequest request =
        RedemptionRequest.builder()
            .userId(userId)
            .fundUnits(fundUnits)
            .customerIban(customerIban)
            .status(PENDING)
            .build();

    RedemptionRequest saved = redemptionRequestRepository.save(request);
    log.info(
        "Created redemption request: id={}, userId={}, fundUnits={}, customerIban={}",
        saved.getId(),
        userId,
        fundUnits,
        customerIban);

    return saved;
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

  private void validateRequest(User user, BigDecimal fundUnits) {
    if (!savingsFundOnboardingService.isOnboardingCompleted(user)) {
      throw new IllegalStateException(
          "User savings fund onboarding not completed: userId=" + user.getId());
    }

    BigDecimal availableUnits = getAvailableFundUnits(user);
    BigDecimal pendingRedemptionUnits = getPendingRedemptionUnits(user.getId());
    BigDecimal effectiveAvailable = availableUnits.subtract(pendingRedemptionUnits);

    if (fundUnits.compareTo(effectiveAvailable) > 0) {
      throw new IllegalArgumentException(
          "Insufficient fund units: requested=" + fundUnits + ", available=" + effectiveAvailable);
    }

    if (fundUnits.compareTo(ZERO) <= 0) {
      throw new IllegalArgumentException("Fund units must be positive: " + fundUnits);
    }
  }

  private BigDecimal getAvailableFundUnits(User user) {
    return ledgerService.getUserAccount(user, FUND_UNITS).getBalance().negate();
  }

  private BigDecimal getPendingRedemptionUnits(Long userId) {
    return redemptionRequestRepository.findByStatus(PENDING).stream()
        .filter(r -> r.getUserId().equals(userId))
        .map(RedemptionRequest::getFundUnits)
        .reduce(ZERO, BigDecimal::add);
  }
}
