package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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

  private final Clock clock;
  private final IssuerService issuerService;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Europe/Tallinn")
  public void runJob() {
    var payments = getReservedPaymentsFromBeforeToday();
    var nav = getNAV();

    for (SavingFundPayment payment : payments) {
      issuerService.processPayment(payment, nav);
    }
  }

  public List<SavingFundPayment> getReservedPaymentsFromBeforeToday() {
    // TODO trust previous services or check 16:00 cutoff also here?
    var reservedPayments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);

    var lastWorkingDay = new PublicHolidays().previousWorkingDay(LocalDate.now(clock));
    var dayAfterLastWorkingDay = lastWorkingDay.plusDays(1);
    var reservedTransactionCutoff =
        dayAfterLastWorkingDay.atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

    return reservedPayments.stream()
        .filter(payment -> payment.getStatusChangedAt().isBefore(reservedTransactionCutoff))
        .collect(Collectors.toList());
  }

  private BigDecimal getNAV() {
    // TODO nav fetching
    return BigDecimal.ONE;
  }
}
