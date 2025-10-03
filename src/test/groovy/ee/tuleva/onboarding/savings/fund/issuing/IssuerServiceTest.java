package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.time.TestClockHolder.clock;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.RoundingMode.HALF_DOWN;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("processes payments")
  void processMessages() {
    var user = sampleUser().build();
    var nav = ONE;
    var paymentAmount = TEN;

    var reservedPaymentFromYesterday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(paymentAmount)
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .userId(user.getId())
            .createdAt(clock.instant().minus(2, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(1, DAYS))
            .build();

    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    issuerService.processPayment(reservedPaymentFromYesterday, nav);

    var issuedUnits = paymentAmount.divide(nav, 5, HALF_DOWN);
    verify(savingsFundLedger, times(1)).issueFundUnitsFromReserved(user, TEN, issuedUnits, nav);
    verify(savingFundPaymentRepository, times(1))
        .changeStatus(reservedPaymentFromYesterday.getId(), ISSUED);
  }
}
