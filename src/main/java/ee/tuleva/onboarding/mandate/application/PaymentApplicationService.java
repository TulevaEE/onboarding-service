package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static ee.tuleva.onboarding.epis.mandate.ApplicationStatus.COMPLETE;
import static ee.tuleva.onboarding.payment.PaymentStatus.PENDING;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import ee.tuleva.onboarding.fund.ApiFundResponse;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.PaymentService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PaymentApplicationService {

  private static final String TULEVA_3RD_PILLAR_FUND_ISIN = "EE3600001707";

  private final PaymentService paymentService;
  private final CashFlowService cashFlowService;
  private final FundRepository fundRepository;
  private final LocaleService localeService;

  public List<Application<PaymentApplicationDetails>> getPaymentApplications(Person person) {
    val pendingPayments = paymentService.getPayments(person, PENDING);
    val cashFlowStatement = cashFlowService.getCashFlowStatement(person);
    val cashTransactions =
        cashFlowStatement.getTransactions().stream().filter(CashFlow::isCash).toList();
    val locale = localeService.getCurrentLocale();
    val fund = fundRepository.findByIsin(TULEVA_3RD_PILLAR_FUND_ISIN);
    val apiFund = new ApiFundResponse(fund, locale);

    val applications = new ArrayList<Application<PaymentApplicationDetails>>();

    for (Payment payment : pendingPayments) {
      for (CashFlow transaction : cashTransactions) {
        if (transaction.getAmount().equals(payment.getAmount())) {
          val application =
              Application.<PaymentApplicationDetails>builder()
                  .id(payment.getId())
                  .creationTime(payment.getCreatedTime())
                  .status(COMPLETE) // TODO
                  .details(
                      PaymentApplicationDetails.builder()
                          .amount(payment.getAmount())
                          .currency(EUR)
                          .targetFund(apiFund)
                          .build())
                  .build();
          applications.add(application);
        }
      }
    }
    return applications;
  }
}
