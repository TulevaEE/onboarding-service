package ee.tuleva.onboarding.savings.fund.issuing;

import static java.math.RoundingMode.HALF_DOWN;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
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

  @Transactional
  void processPayment(IssuingJob.MockPayment payment, BigDecimal nav) {
    var remitter = payment.remitter();
    var unitsAmount = payment.amount().divide(nav, 5, HALF_DOWN); // TODO rounding mode, scale?
    var cashAmount = payment.amount();

    savingsFundLedger.issueFundUnitsFromReserved(remitter, cashAmount, unitsAmount, nav);

    // TODO payment status to ...
  }
}
