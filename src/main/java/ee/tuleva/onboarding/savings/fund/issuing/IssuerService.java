package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.ISSUED;
import static java.math.RoundingMode.HALF_DOWN;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
class IssuerService {

  private final SavingsFundLedger savingsFundLedger;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Transactional
  IssuingResult processPayment(SavingFundPayment payment, BigDecimal nav) {
    var unitsAmount = payment.getAmount().divide(nav, 5, HALF_DOWN); // TODO rounding mode, scale?
    var cashAmount = payment.getAmount();

    var partyId =
        requireNonNull(
            payment.getPartyId(), "Payment missing party id: paymentId=" + payment.getId());
    savingsFundLedger.issueFundUnitsFromReserved(
        partyId, cashAmount, unitsAmount, nav, payment.getId());

    savingFundPaymentRepository.changeStatus(payment.getId(), ISSUED);
    return new IssuingResult(cashAmount, unitsAmount);
  }
}
