package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Profile("dev")
@Service
@Slf4j
@RequiredArgsConstructor
public class IssuingJob {

  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0, 0);
  private static final ZoneId CUTOFF_TIMEZONE = ZoneId.of("Europe/Tallinn");
  private final Clock clock;
  private final IssuerService issuerService;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Scheduled(fixedRateString = "1m")
  public void runJob() {
    var payments = getReservedPaymentsDependingOnCurrentTime();
    var nav = getNAV();

    for (SavingFundPayment payment : payments) {
      issuerService.processPayment(payment, nav);
    }
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
        .collect(Collectors.toList());
  }

  private List<SavingFundPayment> getReservedPaymentsFromBeforeLastWorkingDay() {
    var reservedPayments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);

    var lastWorkingDay = new PublicHolidays().previousWorkingDay(LocalDate.now(clock));

    var reservedTransactionCutoff = getCutoff(lastWorkingDay);

    return reservedPayments.stream()
        .filter(payment -> payment.getReceivedBefore().isBefore(reservedTransactionCutoff))
        .collect(Collectors.toList());
  }

  private Instant getCutoff(LocalDate date) {
    return ZonedDateTime.of(date, CUTOFF_TIME, CUTOFF_TIMEZONE).toInstant();
  }

  private BigDecimal getNAV() {
    // TODO nav fetching
    return BigDecimal.ONE;
  }
}
