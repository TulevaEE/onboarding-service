package ee.tuleva.onboarding.savings.fund.issuing;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssuingJobTest {

  private Clock clock;
  private IssuerService issuerService;
  private SavingFundPaymentRepository paymentRepository;
  private IssuingJob issuingJob;

  @BeforeEach
  void setUp() {

    clock = TestClockHolder.clock;
    paymentRepository = mock(SavingFundPaymentRepository.class);
    issuerService = mock(IssuerService.class);

    issuingJob = new IssuingJob(clock, issuerService, paymentRepository);
  }

  @Test
  @DisplayName("processes RESERVED payments from yesterday, ignores those from today")
  void processMessages() {
    var nav = BigDecimal.ONE;

    var reservedPaymentFromYesterday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .createdAt(clock.instant().minus(2, DAYS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(1, DAYS))
            .build();

    var reservedPaymentToday =
        SavingFundPayment.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("500.00"))
            .currency(Currency.EUR)
            .description("Monthly contribution")
            .remitterIban("EE34370400440532013000")
            .remitterName("John Doe")
            .remitterIdCode("49002010976")
            .beneficiaryIban("EE987654321098765432")
            .beneficiaryName("Tuleva Savings Account")
            .beneficiaryIdCode("87654321")
            .externalId("EXT-12345")
            .createdAt(clock.instant().minus(2, HOURS))
            .status(RESERVED)
            .statusChangedAt(clock.instant().minus(1, HOURS))
            .build();

    when(paymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(List.of(reservedPaymentToday, reservedPaymentFromYesterday));

    issuingJob.runJob();

    verify(issuerService, times(1)).processPayment(reservedPaymentFromYesterday, nav);
    verify(issuerService, never()).processPayment(reservedPaymentToday, nav);
  }
}
