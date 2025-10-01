package ee.tuleva.onboarding.savings.fund.issuing;

import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.util.List;
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

  record MockPayment(User remitter, BigDecimal amount) {}

  @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Europe/Tallinn")
  public void runJob() {
    var payments = getPayments();
    var nav = getNAV();

    for (MockPayment payment : payments) {
      issuerService.processPayment(payment, nav);
    }
  }

  private List<MockPayment> getPayments() {
    return List.of();
  }

  private BigDecimal getNAV() {
    // TODO nav fetching
    return BigDecimal.ONE;
  }
}
