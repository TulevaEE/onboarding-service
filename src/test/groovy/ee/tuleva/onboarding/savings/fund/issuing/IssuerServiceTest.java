package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static java.math.RoundingMode.HALF_DOWN;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuerServiceTest {

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
    var nav = BigDecimal.ONE;
    var paymentAmount = BigDecimal.TEN;
    issuerService.processPayment(new IssuingJob.MockPayment(user, paymentAmount), nav);

    var issuedUnits = paymentAmount.divide(nav, 5, HALF_DOWN);
    verify(savingsFundLedger, times(1)).issueFundUnits(user, BigDecimal.TEN, issuedUnits, nav);

    // TODO assert status change
  }
}
