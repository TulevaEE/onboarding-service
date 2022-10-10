package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.currency.Currency.EUR;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentApplicationService {

  private static final String TULEVA_3RD_PILLAR_FUND_ISIN = "EE3600001707";
  private static final Duration GRACE_PERIOD = Duration.ofMinutes(30);

  private final PaymentService paymentService;
  private final CashFlowService cashFlowService;
  private final FundRepository fundRepository;
  private final LocaleService localeService;

  public List<Application<PaymentApplicationDetails>> getPaymentApplications(Person person) {
    val payments = paymentService.getPayments(person);
    val cashFlowStatement = cashFlowService.getCashFlowStatement(person);
    val locale = localeService.getCurrentLocale();
    val fund = fundRepository.findByIsin(TULEVA_3RD_PILLAR_FUND_ISIN);
    val apiFund = new ApiFundResponse(fund, locale);

    val applications = new ArrayList<Application<PaymentApplicationDetails>>();

    log.info("Payments: {}", payments);

    log.info("Cash flow statement: {}", cashFlowStatement);

    val linkedCashFlow = getLinkedCashFlow(payments, cashFlowStatement.getTransactions());

    log.info("Linked cash flow: {}", linkedCashFlow);

    for (val entry : linkedCashFlow.entrySet()) {
      val payment = entry.getKey();
      val linkedCash = entry.getValue();
      if (linkedCash.isEmpty() || !cashIsBalanced(linkedCash)) {
        log.info("Cash is not balanced or no cash entries for {}", payment.getId());
        applications.add(createApplication(payment, apiFund, ApplicationStatus.PENDING));
      } else if (cashIsBalanced(linkedCash)) {
        if (hasTulevaContribution(linkedCash)) {
          log.info("Payment {} has Tuleva fund contribution, marking as complete", payment.getId());
          applications.add(createApplication(payment, apiFund, ApplicationStatus.COMPLETE));
        } else {
          log.info("Payment {} does not have a Tuleva fund contribution yet", payment.getId());
          applications.add(createApplication(payment, apiFund, ApplicationStatus.PENDING));
        }
      }
    }
    return applications;
  }

  private boolean hasTulevaContribution(List<CashFlow> linkedCash) {
    return linkedCash.stream()
        .filter(it -> Objects.equals(it.getIsin(), TULEVA_3RD_PILLAR_FUND_ISIN))
        .anyMatch(CashFlow::isContribution);
  }

  private static boolean cashIsBalanced(List<CashFlow> linkedCash) {
    return linkedCash.stream()
            .filter(CashFlow::isCash)
            .map(CashFlow::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(BigDecimal.ZERO)
        == 0;
  }

  private Map<Payment, List<CashFlow>> getLinkedCashFlow(
      List<Payment> payments, List<CashFlow> cashFlow) {
    val remainingCashFlow = new ArrayList<>(cashFlow.stream().sorted().toList());
    val linkedCashFlow = new TreeMap<Payment, List<CashFlow>>();
    for (Payment payment : payments.stream().sorted().toList()) {
      val payIn = linkedPayIn(remainingCashFlow, payment);
      val payOut = linkedPayOut(remainingCashFlow, payIn);
      val contribution = linkedContribution(remainingCashFlow, payOut);

      val paymentCashFlow =
          Stream.of(payIn, payOut, contribution)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .toList();

      log.info("Payment {} has linked cash flow: {}", payment.getId(), paymentCashFlow);

      paymentCashFlow.forEach(remainingCashFlow::remove);
      linkedCashFlow.put(payment, paymentCashFlow);
    }
    return linkedCashFlow;
  }

  private Optional<CashFlow> linkedContribution(
      List<CashFlow> remainingCashFlow, Optional<CashFlow> payOut) {
    return payOut.flatMap(
        cashFlow ->
            remainingCashFlow.stream()
                .filter(
                    isContributionOnOrAfterTimeWithAmount(
                        cashFlow.getPriceTime(), cashFlow.getAmount().negate()))
                .findFirst());
  }

  private Optional<CashFlow> linkedPayOut(
      List<CashFlow> remainingCashFlow, Optional<CashFlow> payIn) {
    return payIn.flatMap(
        cashFlow ->
            remainingCashFlow.stream()
                .filter(
                    isCashAfterTimeWithAmount(
                        cashFlow.getPriceTime(), cashFlow.getAmount().negate()))
                .findFirst());
  }

  private Optional<CashFlow> linkedPayIn(List<CashFlow> remainingCashFlow, Payment payment) {
    return remainingCashFlow.stream()
        .filter(isCashAfterGraceTimeWithAmount(payment.getCreatedTime(), payment.getAmount()))
        .findFirst();
  }

  private Predicate<CashFlow> isCashAfterGraceTimeWithAmount(Instant time, BigDecimal amount) {
    return ((Predicate<CashFlow>) CashFlow::isCash)
        .and(isAfterTimeWithGrace(time))
        .and(hasSameAmount(amount));
  }

  private Predicate<CashFlow> isCashAfterTimeWithAmount(Instant time, BigDecimal amount) {
    return ((Predicate<CashFlow>) CashFlow::isCash)
        .and((cashFlow -> !cashFlow.getTime().isBefore(time)))
        .and(hasSameAmount(amount));
  }

  private Predicate<CashFlow> isContributionOnOrAfterTimeWithAmount(
      Instant time, BigDecimal amount) {
    return ((Predicate<CashFlow>) CashFlow::isContribution)
        .and((cashFlow -> !cashFlow.getTime().isBefore(time)))
        .and(hasSameAmount(amount));
  }

  private Predicate<CashFlow> hasSameAmount(BigDecimal amount) {
    return (cashFlow) ->
        cashFlow
                .getAmount()
                .setScale(2, RoundingMode.HALF_UP)
                .subtract(amount)
                .abs()
                .compareTo(new BigDecimal("0.01"))
            <= 0;
  }

  private Predicate<CashFlow> isAfterTimeWithGrace(Instant time) {
    return (cashFlow) -> cashFlow.isAfter(time.minus(GRACE_PERIOD));
  }

  private Application<PaymentApplicationDetails> createApplication(
      Payment payment, ApiFundResponse apiFund, ApplicationStatus status) {
    return Application.<PaymentApplicationDetails>builder()
        .id(payment.getId())
        .creationTime(payment.getCreatedTime())
        .status(status)
        .details(
            PaymentApplicationDetails.builder()
                .amount(payment.getAmount())
                .currency(EUR)
                .targetFund(apiFund)
                .build())
        .build();
  }
}
