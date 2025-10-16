package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class FundAccountPaymentJob {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final TransactionTemplate transactionTemplate;

  @Scheduled(fixedRateString = "1m")
  public void runJob() {
    try {
      transactionTemplate.executeWithoutResult(ignored -> createPaymentRequest());
    } catch (Exception e) {
      log.error("Creating of subscriptions payment to investment account failed", e);
    }
  }

  void createPaymentRequest() {
    var payments = savingFundPaymentRepository.findPaymentsWithStatus(ISSUED);
    var total = payments.stream().map(SavingFundPayment::getAmount).reduce(ZERO, BigDecimal::add);
    payments.forEach(
        payment -> savingFundPaymentRepository.changeStatus(payment.getId(), PROCESSED));
    var id = UUID.randomUUID();
    var paymentRequest =
        PaymentRequest.tulevaPaymentBuilder(id)
            .remitterIban(swedbankAccountConfiguration.getAccountIban(DEPOSIT_EUR))
            .beneficiaryName("Tuleva Fondid AS")
            .beneficiaryIban(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
            .amount(total)
            .description("Subscriptions")
            .build();
    log.info(
        "Preparing subscriptions payment to investment account with the amount of {} EUR", total);
    swedbankGatewayClient.sendPaymentRequest(paymentRequest, id);
  }
}
