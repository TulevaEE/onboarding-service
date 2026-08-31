package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.FundNavProvider;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.notification.IssuingCompletedEvent;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class IssuingJob {

  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0, 0);
  private static final ZoneId CUTOFF_TIMEZONE = ZoneId.of("Europe/Tallinn");
  private final Clock clock;
  private final IssuerService issuerService;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final FundNavProvider navProvider;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "IssuingJob_runJob", lockAtMostFor = "50s", lockAtLeastFor = "10s")
  @Transactional
  public void runJob() {
    var cutoff = getCutoffForProcessing();
    var payments = getReservedPaymentsReceivedBefore(cutoff);
    if (payments.isEmpty()) {
      log.info("No payments to issue, skipping");
      return;
    }
    var previousCutoff = getPreviousCutoff(cutoff);
    payments.forEach(
        payment -> {
          if (payment.getReceivedBefore() != null
              && payment.getReceivedBefore().isBefore(previousCutoff)) {
            log.error(
                "Old payment detected: payment {} was received at {} which is before the previous cutoff time {}",
                payment.getId(),
                payment.getReceivedBefore(),
                previousCutoff);
          }
        });
    log.info("Running issuing job for {} payments", payments.size());
    var nav = navProvider.getVerifiedNavForIssuingAndRedeeming(TKF100, dealingDate(cutoff));
    log.info("Running issuing job for {} payments with nav {}", payments.size(), nav);
    var totalAmount = ZERO;
    var totalFundUnits = ZERO;
    for (SavingFundPayment payment : payments) {
      var result = issuerService.processPayment(payment, nav);
      totalAmount = totalAmount.add(result.cashAmount());
      totalFundUnits = totalFundUnits.add(result.fundUnits());
    }
    log.info("Issuing job completed: processed {} payments", payments.size());
    eventPublisher.publishEvent(
        new IssuingCompletedEvent(payments.size(), totalAmount, totalFundUnits, nav));
  }

  private Instant getCutoffForProcessing() {
    var today = todayInTallinn();
    var publicHolidays = new PublicHolidays();
    var lastWorkingDay = publicHolidays.previousWorkingDay(today);
    if (clock.instant().isBefore(getCutoff(today)) || !publicHolidays.isWorkingDay(today)) {
      return getCutoff(publicHolidays.previousWorkingDay(lastWorkingDay));
    }
    return getCutoff(lastWorkingDay);
  }

  private List<SavingFundPayment> getReservedPaymentsReceivedBefore(Instant cutoff) {
    return savingFundPaymentRepository.findPaymentsWithStatus(RESERVED).stream()
        .filter(
            payment -> {
              var receivedBefore = payment.getReceivedBefore();
              return receivedBefore != null && receivedBefore.isBefore(cutoff);
            })
        .toList();
  }

  private Instant getPreviousCutoff(Instant cutoff) {
    return getCutoff(new PublicHolidays().previousWorkingDay(dealingDate(cutoff)));
  }

  private LocalDate todayInTallinn() {
    return clock.instant().atZone(CUTOFF_TIMEZONE).toLocalDate();
  }

  private LocalDate dealingDate(Instant cutoff) {
    return cutoff.atZone(CUTOFF_TIMEZONE).toLocalDate();
  }

  private Instant getCutoff(LocalDate date) {
    return ZonedDateTime.of(date, CUTOFF_TIME, CUTOFF_TIMEZONE).toInstant();
  }
}
