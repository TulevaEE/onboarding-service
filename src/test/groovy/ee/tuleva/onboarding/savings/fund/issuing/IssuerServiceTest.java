package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.RoundingMode.HALF_DOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuerServiceTest {

  private UserService userService;
  private SavingsFundLedger savingsFundLedger;
  private SavingFundPaymentRepository savingFundPaymentRepository;
  private IssuerService issuerService;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    savingFundPaymentRepository = mock(SavingFundPaymentRepository.class);
    savingsFundLedger = mock(SavingsFundLedger.class);

    issuerService = new IssuerService(userService, savingsFundLedger, savingFundPaymentRepository);
  }

  @Test
  void processPayment_issuesFundUnitsAndChangesStatus() {
    var user = sampleUser().build();
    var payment = aPayment().amount(TEN).userId(user.getId()).status(RESERVED).build();

    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    issuerService.processPayment(payment, ONE);

    var issuedUnits = TEN.divide(ONE, 5, HALF_DOWN);
    verify(savingsFundLedger)
        .issueFundUnitsFromReserved(user, TEN, issuedUnits, ONE, payment.getId());
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), ISSUED);
  }

  @Test
  void processPayment_returnsIssuingResult() {
    var user = sampleUser().build();
    var nav = new BigDecimal("9.9918");
    var paymentAmount = new BigDecimal("500.00");

    var payment = aPayment().amount(paymentAmount).userId(user.getId()).status(RESERVED).build();

    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);

    var result = issuerService.processPayment(payment, nav);

    var expectedUnits = paymentAmount.divide(nav, 5, HALF_DOWN);
    assertThat(result).isEqualTo(new IssuingResult(paymentAmount, expectedUnits));
  }
}
