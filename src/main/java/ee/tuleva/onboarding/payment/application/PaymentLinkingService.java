package ee.tuleva.onboarding.payment.application;

import static ee.tuleva.onboarding.currency.Currency.EUR;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.mandate.application.Application;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLinkingService {

  public static final String TULEVA_3RD_PILLAR_FUND_ISIN = "EE3600001707";
  private static final Duration GRACE_PERIOD = Duration.ofMinutes(30);

  private final PaymentService paymentService;
  private final CashFlowService cashFlowService;
  private final FundRepository fundRepository;
  private final LocaleService localeService;

  private final Clock clock;

  private final PublicHolidays publicHolidays;

  public List<Application<PaymentApplicationDetails>> getPaymentApplications(Person person) {
    final var payments = paymentService.getThirdPillarPayments(person);
    final var cashFlowStatement = cashFlowService.getCashFlowStatement(person);
    final var locale = localeService.getCurrentLocale();
    final var fund = fundRepository.findByIsin(TULEVA_3RD_PILLAR_FUND_ISIN);
    final var apiFund = new ApiFundResponse(fund, locale);

    final var applications = new ArrayList<Application<PaymentApplicationDetails>>();

    final var linkedCashFlow = getLinkedCashFlow(payments, cashFlowStatement.getTransactions());

    log.info("Linked cash flow: {}", linkedCashFlow);

    for (final var entry : linkedCashFlow.entrySet()) {
      final var payment = entry.getKey();
      final var linkedCash = entry.getValue();
      if (linkedCash.isEmpty() || !cashIsBalanced(linkedCash)) {
        if (hasRefund(linkedCash)) {
          log.info("Payment {} has a refund", payment.getId());
          applications.add(createApplication(payment, apiFund, ApplicationStatus.FAILED));
        } else if (isTimeMoreThanThreeDaysEarlierThanReference(
            LocalDate.now(clock), payment.getCreatedTime())) {
          log.info("Payment is older than three working days {}", payment.getId());
          applications.add(createApplication(payment, apiFund, ApplicationStatus.FAILED));
        } else {
          log.info("Cash is not balanced or no cash entries for {}", payment.getId());
          applications.add(createApplication(payment, apiFund, ApplicationStatus.PENDING));
        }
      } else if (cashIsBalanced(linkedCash)) {
        if (hasTulevaContribution(linkedCash)) {
          log.info("Payment {} has Tuleva fund contribution, marking as complete", payment.getId());
          applications.add(createApplication(payment, apiFund, ApplicationStatus.COMPLETE));
        } else {
          if (isTimeMoreThanThreeDaysEarlierThanReference(
              LocalDate.now(clock), payment.getCreatedTime())) {
            log.info(
                "Payment {} does not have a Tuleva fund contribution yet but is older than 5 days, marking as failed",
                payment.getId());
            applications.add(createApplication(payment, apiFund, ApplicationStatus.FAILED));
          } else {
            log.info("Payment {} does not have a Tuleva fund contribution yet", payment.getId());
            applications.add(createApplication(payment, apiFund, ApplicationStatus.PENDING));
          }
        }
      }
    }
    return applications;
  }

  private boolean isTimeMoreThanThreeDaysEarlierThanReference(
      LocalDate referencePoint, Instant timeToCheck) {
    final var timeToCheckPlusThreeWorkingDays =
        publicHolidays.addWorkingDays(LocalDate.ofInstant(timeToCheck, clock.getZone()), 3);
    return !timeToCheckPlusThreeWorkingDays.isAfter(referencePoint);
  }

  private boolean hasRefund(List<CashFlow> linkedCash) {
    return linkedCash.stream().anyMatch(CashFlow::isRefund);
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
    final var remainingCashFlow = new ArrayList<>(cashFlow.stream().sorted().toList());
    final var linkedCashFlow = new TreeMap<Payment, List<CashFlow>>();
    for (Payment payment : payments.stream().sorted().toList()) {
      final var payIn = linkedPayIn(remainingCashFlow, payment);
      final var refund = linkedRefund(remainingCashFlow, payIn);
      final var payOut = linkedPayOut(remainingCashFlow, payIn);
      final var contribution = linkedContribution(remainingCashFlow, payOut);

      final var paymentCashFlow =
          Stream.of(payIn, refund, payOut, contribution).flatMap(Optional::stream).toList();

      log.info("Payment {} has linked cash flow: {}", payment.getId(), paymentCashFlow);

      paymentCashFlow.forEach(remainingCashFlow::remove);
      linkedCashFlow.put(payment, paymentCashFlow);
    }
    return linkedCashFlow;
  }

  private Optional<CashFlow> linkedRefund(
      List<CashFlow> remainingCashFlow, Optional<CashFlow> payIn) {
    return payIn.flatMap(
        cashFlow ->
            remainingCashFlow.stream()
                .filter(
                    isRefundOnOrAfterTimeWithAmount(
                        cashFlow.getTime(), cashFlow.getAmount().negate()))
                .findFirst());
  }

  private Predicate<CashFlow> isRefundOnOrAfterTimeWithAmount(Instant time, BigDecimal amount) {
    return ((Predicate<CashFlow>) CashFlow::isRefund)
        .and((cashFlow -> !cashFlow.getTime().isBefore(time)))
        .and(hasSameAmount(amount));
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

  private Predicate<CashFlow> isCashAfterGraceTimeWithAmount(
      Instant paymentTime, BigDecimal amount) {
    return ((Predicate<CashFlow>) CashFlow::isCash)
        .and(isAfterTimeWithGrace(paymentTime))
        .and(isLessThanThreeWorkingDaysAfter(paymentTime))
        .and(hasSameAmount(amount));
  }

  private Predicate<CashFlow> isLessThanThreeWorkingDaysAfter(Instant paymentTime) {
    return cashFlow -> {
      final var timeToCheckPlusThreeWorkingDays =
          publicHolidays.addWorkingDays(LocalDate.ofInstant(paymentTime, clock.getZone()), 3);
      return !timeToCheckPlusThreeWorkingDays.isBefore(
          LocalDate.ofInstant(cashFlow.getTime(), clock.getZone()));
    };
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
