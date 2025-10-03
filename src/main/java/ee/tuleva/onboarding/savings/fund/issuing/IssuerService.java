package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static java.math.RoundingMode.HALF_DOWN;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserService;
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

  private final UserService userService;
  private final SavingsFundLedger savingsFundLedger;
  private final SavingFundPaymentRepository savingFundPaymentRepository;

  @Transactional
  void processPayment(SavingFundPayment payment, BigDecimal nav) {
    var unitsAmount = payment.getAmount().divide(nav, 5, HALF_DOWN); // TODO rounding mode, scale?
    var cashAmount = payment.getAmount();

    var user = userService.getByIdOrThrow(payment.getUserId());
    savingsFundLedger.issueFundUnitsFromReserved(user, cashAmount, unitsAmount, nav);

    savingFundPaymentRepository.changeStatus(payment.getId(), ISSUED);
  }
}
