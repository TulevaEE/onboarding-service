package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPaymentFixture.aPayment;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.RoundingMode.HALF_DOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuerServiceTest {

  private SavingsFundLedger savingsFundLedger;
  private SavingFundPaymentRepository savingFundPaymentRepository;
  private IssuerService issuerService;

  @BeforeEach
  void setUp() {
    savingFundPaymentRepository = mock(SavingFundPaymentRepository.class);
    savingsFundLedger = mock(SavingsFundLedger.class);

    issuerService = new IssuerService(savingsFundLedger, savingFundPaymentRepository);
  }

  @Test
  void processPayment_issuesFundUnitsAndChangesStatus() {
    var party = new PartyId(PERSON, "38812121215");
    var payment = aPayment().amount(TEN).partyId(party).status(RESERVED).build();

    issuerService.processPayment(payment, ONE);

    var issuedUnits = TEN.divide(ONE, 5, HALF_DOWN);
    verify(savingsFundLedger)
        .issueFundUnitsFromReserved(party, TEN, issuedUnits, ONE, payment.getId());
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), ISSUED);
  }

  @Test
  void processPayment_returnsIssuingResult() {
    var party = new PartyId(PERSON, "38812121215");
    var nav = new BigDecimal("9.9918");
    var paymentAmount = new BigDecimal("500.00");

    var payment = aPayment().amount(paymentAmount).partyId(party).status(RESERVED).build();

    var result = issuerService.processPayment(payment, nav);

    var expectedUnits = paymentAmount.divide(nav, 5, HALF_DOWN);
    assertThat(result).isEqualTo(new IssuingResult(paymentAmount, expectedUnits));
  }
}
