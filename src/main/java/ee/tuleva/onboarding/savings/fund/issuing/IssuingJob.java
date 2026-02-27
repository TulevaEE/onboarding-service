package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import ee.tuleva.onboarding.savings.fund.notification.IssuingCompletedEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
  public void runJob() {
    var payments = getReservedPaymentsDependingOnCurrentTime();
    if (payments.isEmpty()) {
      log.info("No payments to issue, skipping");
      return;
    }
    log.info("Running issuing job for {} payments", payments.size());
    var nav = getNAV();
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

  private List<SavingFundPayment> getReservedPaymentsDependingOnCurrentTime() {
    var today = todayInTallinn();
    var todaysCutoff = getCutoff(today);
    var currentTime = clock.instant();
    var isTodayWorkingDay = new PublicHolidays().isWorkingDay(today);
    if (currentTime.isBefore(todaysCutoff) || !isTodayWorkingDay) {
      return getReservedPaymentsFromBeforeSecondToLastWorkingDay();
    }

    return getReservedPaymentsFromBeforeLastWorkingDay();
  }

  private List<SavingFundPayment> getReservedPaymentsFromBeforeSecondToLastWorkingDay() {
    var reservedPayments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);

    var publicHolidays = new PublicHolidays();
    var secondToLastWorkingDay =
        publicHolidays.previousWorkingDay(publicHolidays.previousWorkingDay(todayInTallinn()));

    var reservedTransactionCutoff = getCutoff(secondToLastWorkingDay);

    return reservedPayments.stream()
        .filter(payment -> payment.getReceivedBefore().isBefore(reservedTransactionCutoff))
        .toList();
  }

  private List<SavingFundPayment> getReservedPaymentsFromBeforeLastWorkingDay() {
    var reservedPayments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);

    var lastWorkingDay = new PublicHolidays().previousWorkingDay(todayInTallinn());

    var reservedTransactionCutoff = getCutoff(lastWorkingDay);

    return reservedPayments.stream()
        .filter(payment -> payment.getReceivedBefore().isBefore(reservedTransactionCutoff))
        .toList();
  }

  private LocalDate todayInTallinn() {
    return clock.instant().atZone(CUTOFF_TIMEZONE).toLocalDate();
  }

  private Instant getCutoff(LocalDate date) {
    return ZonedDateTime.of(date, CUTOFF_TIME, CUTOFF_TIMEZONE).toInstant();
  }

  private BigDecimal getNAV() {
    return navProvider.getVerifiedNavForIssuingAndRedeeming(TKF100);
  }
}
