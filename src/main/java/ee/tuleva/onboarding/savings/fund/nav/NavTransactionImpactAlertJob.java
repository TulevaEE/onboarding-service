package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.event.FundPositionsImported;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class NavTransactionImpactAlertJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0);

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final FundNavProvider fundNavProvider;
  private final InvestmentParameterRepository investmentParameterRepository;
  private final OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  private volatile LocalDate lastAlertDate;
  private final Set<String> sentAlerts = ConcurrentHashMap.newKeySet();

  @EventListener(classes = FundPositionsImported.class)
  void onFundPositionsImported() {
    try {
      checkAll();
    } catch (Exception e) {
      log.error("NAV transaction impact alert failed", e);
    }
  }

  private void checkAll() {
    LocalDate today = clock.instant().atZone(TALLINN).toLocalDate();
    if (!publicHolidays.isWorkingDay(today)) {
      return;
    }

    resetIdempotencyGuardIfNewDay(today);

    LocalDate navDate = publicHolidays.previousWorkingDay(today);

    checkTkf100Volume(today, navDate);
    checkPevaRava(today, navDate);
    checkR16BookingDay(today, navDate);
  }

  private void resetIdempotencyGuardIfNewDay(LocalDate today) {
    if (!today.equals(lastAlertDate)) {
      sentAlerts.clear();
      lastAlertDate = today;
    }
  }

  private void sendOnce(String alertKey, String message) {
    if (sentAlerts.add(alertKey)) {
      log.info("{}", message);
      notificationService.sendMessage(message, SAVINGS);
    }
  }

  private void checkTkf100Volume(LocalDate today, LocalDate navDate) {
    try {
      BigDecimal threshold =
          investmentParameterRepository.findLatestValue(NAV_IMPACT_VOLUME_THRESHOLD, today);

      Instant cutoff = ZonedDateTime.of(navDate, CUTOFF_TIME, TALLINN).toInstant();

      List<SavingFundPayment> reservedPayments =
          savingFundPaymentRepository.findPaymentsWithStatus(RESERVED).stream()
              .filter(p -> p.getReceivedBefore() != null && p.getReceivedBefore().isBefore(cutoff))
              .toList();
      BigDecimal subscriptionEur =
          reservedPayments.stream().map(SavingFundPayment::getAmount).reduce(ZERO, BigDecimal::add);

      List<RedemptionRequest> redemptions =
          redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff);
      BigDecimal redemptionEur = estimateRedemptionEur(redemptions);

      BigDecimal totalVolume = subscriptionEur.add(redemptionEur);

      if (totalVolume.compareTo(threshold) >= 0) {
        sendOnce(
            "TKF100",
            formatTkf100Alert(
                navDate,
                subscriptionEur,
                redemptionEur,
                totalVolume,
                redemptions.size(),
                reservedPayments.size()));
      }
    } catch (Exception e) {
      log.error("TKF100 volume check failed, continuing with pension fund checks", e);
    }
  }

  private BigDecimal estimateRedemptionEur(List<RedemptionRequest> redemptions) {
    if (redemptions.isEmpty()) {
      return ZERO;
    }
    try {
      BigDecimal nav = fundNavProvider.getDisplayNav(TKF100);
      return redemptions.stream()
          .map(r -> r.getFundUnits().multiply(nav))
          .reduce(ZERO, BigDecimal::add);
    } catch (Exception e) {
      log.warn("NAV lookup failed, estimating redemptions from requestedAmount", e);
      return redemptions.stream()
          .map(RedemptionRequest::getRequestedAmount)
          .reduce(ZERO, BigDecimal::add);
    }
  }

  private void checkPevaRava(LocalDate today, LocalDate navDate) {
    if (isPevaRavaExecutionDate(today)) {
      sendOnce(
          "PEVA_RAVA",
          "⚠️ NAV IMPACT ALERT — PEVA/RAVA execution date (navDate=%s)\n".formatted(navDate)
              + "  All Pillar 2 fund switches and exits will use today's NAV.\n"
              + "  Funds affected: TUK75, TUK00\n"
              + "  Manual NAV verification strongly recommended.");
    }
  }

  private void checkR16BookingDay(LocalDate today, LocalDate navDate) {
    if (isR16BookingDay(today)) {
      sendOnce(
          "R16",
          "⚠️ NAV IMPACT ALERT — R16 booking day (navDate=%s)\n".formatted(navDate)
              + "  Fondimaksed + ühekordsed väljamaksed: Pensionikeskus will book unit redemptions\n"
              + "  using today's NAV (12:00-16:00).\n"
              + "  Funds affected: TUK75, TUK00, TUV100\n"
              + "  Manual NAV verification recommended.");
    }
  }

  boolean isPevaRavaExecutionDate(LocalDate today) {
    int year = today.getYear();
    return today.equals(publicHolidays.nextWorkingDay(LocalDate.of(year, 1, 1)))
        || today.equals(publicHolidays.nextWorkingDay(LocalDate.of(year, 5, 1)))
        || today.equals(adjustedSep1(year));
  }

  private LocalDate adjustedSep1(int year) {
    LocalDate sep1 = LocalDate.of(year, 9, 1);
    return publicHolidays.isWorkingDay(sep1) ? sep1 : publicHolidays.nextWorkingDay(sep1);
  }

  boolean isR16BookingDay(LocalDate today) {
    LocalDate the15th = today.withDayOfMonth(Math.min(15, today.lengthOfMonth()));
    LocalDate adjusted =
        publicHolidays.isWorkingDay(the15th) ? the15th : publicHolidays.nextWorkingDay(the15th);
    return today.equals(adjusted);
  }

  private String formatTkf100Alert(
      LocalDate navDate,
      BigDecimal subscriptionEur,
      BigDecimal redemptionEur,
      BigDecimal totalVolume,
      int redemptionCount,
      int subscriptionCount) {
    BigDecimal impactAt1bp = totalVolume.multiply(new BigDecimal("0.0001")).setScale(2, HALF_UP);
    BigDecimal impactAt10bp = totalVolume.multiply(new BigDecimal("0.001")).setScale(2, HALF_UP);
    BigDecimal impactAt100bp = totalVolume.multiply(new BigDecimal("0.01")).setScale(2, HALF_UP);

    return "⚠️ NAV IMPACT ALERT — TKF100 (navDate=%s)\n".formatted(navDate)
        + String.format(
            Locale.US,
            "  Pending Subscriptions: %,.2f EUR (%d payments)\n",
            subscriptionEur,
            subscriptionCount)
        + String.format(
            Locale.US,
            "  Pending Redemptions: %,.2f EUR (%d requests)\n",
            redemptionEur,
            redemptionCount)
        + String.format(Locale.US, "  Total Volume: %,.2f EUR\n", totalVolume)
        + String.format(
            Locale.US,
            "  Impact: 1bp = %,.2f EUR, 10bp = %,.2f EUR, 100bp = %,.2f EUR\n",
            impactAt1bp,
            impactAt10bp,
            impactAt100bp)
        + "  Manual NAV verification recommended.";
  }
}
