package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
  private final SavingsFundNavProvider navProvider;

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
    for (SavingFundPayment payment : payments) {
      issuerService.processPayment(payment, nav);
    }
    log.info("Issuing job completed: processed {} payments", payments.size());
  }

  private List<SavingFundPayment> getReservedPaymentsDependingOnCurrentTime() {
    var todaysCutoff = getCutoff(LocalDate.now(clock));
    var currentTime = clock.instant();
    var isTodayWorkingDay =
        new PublicHolidays().isWorkingDay(currentTime.atZone(CUTOFF_TIMEZONE).toLocalDate());
    if (currentTime.isBefore(todaysCutoff) || !isTodayWorkingDay) {
      return getReservedPaymentsFromBeforeSecondToLastWorkingDay();
    }

    return getReservedPaymentsFromBeforeLastWorkingDay();
  }

  private List<SavingFundPayment> getReservedPaymentsFromBeforeSecondToLastWorkingDay() {
    var reservedPayments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);

    var publicHolidays = new PublicHolidays();
    var secondToLastWorkingDay =
        publicHolidays.previousWorkingDay(publicHolidays.previousWorkingDay(LocalDate.now(clock)));

    var reservedTransactionCutoff = getCutoff(secondToLastWorkingDay);

    return reservedPayments.stream()
        .filter(payment -> payment.getReceivedBefore().isBefore(reservedTransactionCutoff))
        .collect(toList());
  }

  private List<SavingFundPayment> getReservedPaymentsFromBeforeLastWorkingDay() {
    var reservedPayments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);

    var lastWorkingDay = new PublicHolidays().previousWorkingDay(LocalDate.now(clock));

    var reservedTransactionCutoff = getCutoff(lastWorkingDay);

    return reservedPayments.stream()
        .filter(payment -> payment.getReceivedBefore().isBefore(reservedTransactionCutoff))
        .collect(toList());
  }

  private Instant getCutoff(LocalDate date) {
    return ZonedDateTime.of(date, CUTOFF_TIME, CUTOFF_TIMEZONE).toInstant();
  }

  private BigDecimal getNAV() {
    return navProvider.getCurrentNavForIssuing();
  }
}
