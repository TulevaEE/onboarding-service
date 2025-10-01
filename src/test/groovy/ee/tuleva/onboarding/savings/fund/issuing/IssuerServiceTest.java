package ee.tuleva.onboarding.savings.fund.issuing;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Disabled
@ExtendWith(MockitoExtension.class)
class IssuerServiceTest {
  /*
  private SavingsFundLedger savingsFundLedger;
  private IssuerService issuerService;

  @BeforeEach
  void setUp() {

    savingsFundLedger = mock(SavingsFundLedger.class);

    issuerService = new IssuerService(savingsFundLedger);
  }

  @Test
  @DisplayName("processes payments")
  void processMessages() {
    var user = sampleUser().build();
    var nav = ONE;
    var paymentAmount = TEN;
    issuerService.processPayment(new IssuingJob.MockPayment(user, paymentAmount), nav);

    var issuedUnits = paymentAmount.divide(nav, 5, HALF_DOWN);
    verify(savingsFundLedger, times(1)).issueFundUnitsFromReserved(user, TEN, issuedUnits, nav);

    // TODO assert status change
  }*/
}
