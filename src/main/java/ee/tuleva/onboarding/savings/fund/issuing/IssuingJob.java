package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;

import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
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

  private final IssuerService issuerService;

  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Europe/Tallinn")
  public void runJob() {
    var payments = savingFundPaymentRepository.findPaymentsWithStatus(RESERVED);
    var nav = getNAV();

    for (SavingFundPayment payment : payments) {
      issuerService.processPayment(payment, nav);
    }
  }

  private BigDecimal getNAV() {
    // TODO nav fetching
    return BigDecimal.ONE;
  }
}
