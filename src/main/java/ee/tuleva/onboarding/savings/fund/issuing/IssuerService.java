package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static java.math.RoundingMode.HALF_DOWN;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("dev")
@Service
@Slf4j
@RequiredArgsConstructor
class IssuerService {

  private final SavingsFundLedger savingsFundLedger;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Transactional
  void processPayment(SavingFundPayment payment, BigDecimal nav) {
    // var remitter = payment.();
    var unitsAmount = payment.getAmount().divide(nav, 5, HALF_DOWN); // TODO rounding mode, scale?
    var cashAmount = payment.getAmount();

    // savingsFundLedger.issueFundUnitsFromReserved(remitter, cashAmount, unitsAmount, nav);

    // TODO payment status to ...
    savingFundPaymentRepository.changeStatus(payment.getId(), PROCESSED);
  }
}
