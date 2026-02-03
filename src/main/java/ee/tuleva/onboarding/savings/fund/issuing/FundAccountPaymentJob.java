package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.event.TrackableEventType.SUBSCRIPTION_BATCH_CREATED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.banking.BankAccountConfiguration;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class FundAccountPaymentJob {

  private final BankAccountConfiguration bankAccountConfiguration;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final TransactionTemplate transactionTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final EndToEndIdConverter endToEndIdConverter;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "FundAccountPaymentJob_runJob",
      lockAtMostFor = "50s",
      lockAtLeastFor = "10s")
  public void runJob() {
    try {
      transactionTemplate.executeWithoutResult(ignored -> createPaymentRequest());
    } catch (Exception e) {
      log.error("Creating of subscriptions payment to investment account failed", e);
    }
  }

  void createPaymentRequest() {
    var payments = savingFundPaymentRepository.findPaymentsWithStatus(ISSUED);
    if (payments.isEmpty()) {
      return;
    }
    var total = payments.stream().map(SavingFundPayment::getAmount).reduce(ZERO, BigDecimal::add);
    payments.forEach(
        payment -> savingFundPaymentRepository.changeStatus(payment.getId(), PROCESSED));
    var id = UUID.randomUUID();

    var paymentIds = payments.stream().map(SavingFundPayment::getId).toList();
    eventPublisher.publishEvent(
        new TrackableSystemEvent(
            SUBSCRIPTION_BATCH_CREATED,
            Map.of(
                "batchId",
                id.toString(),
                "paymentIds",
                paymentIds,
                "paymentCount",
                payments.size(),
                "totalAmount",
                total)));

    var paymentRequest =
        PaymentRequest.tulevaPaymentBuilder(endToEndIdConverter.toEndToEndId(id))
            .remitterIban(bankAccountConfiguration.getAccountIban(DEPOSIT_EUR))
            .beneficiaryName("Tuleva Täiendav Kogumisfond")
            .beneficiaryIban(bankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
            .amount(total)
            .description("Subscriptions")
            .build();
    log.info(
        "Preparing subscriptions payment to investment account with the amount of {} EUR", total);
    eventPublisher.publishEvent(new RequestPaymentEvent(paymentRequest, id));
  }
}
