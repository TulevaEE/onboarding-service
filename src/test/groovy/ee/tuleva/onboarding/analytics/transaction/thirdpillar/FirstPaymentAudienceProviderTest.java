package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.notification.email.firstpayment.FirstThirdPillarPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirstPaymentAudienceProviderTest {

  @Mock private FirstThirdPillarPaymentRepository firstPaymentRepository;
  @InjectMocks private FirstPaymentAudienceProvider provider;

  @Test
  void oldestOwnPaymentDateDelegatesToTheRepository() {
    LocalDate oldest = LocalDate.of(2020, 1, 1);
    given(firstPaymentRepository.oldestOwnPaymentDate()).willReturn(Optional.of(oldest));

    assertThat(provider.oldestOwnPaymentDate()).contains(oldest);
  }

  @Test
  void oldestOwnPaymentDateIsEmptyWhenTheRepositoryHasNoPayments() {
    given(firstPaymentRepository.oldestOwnPaymentDate()).willReturn(Optional.empty());

    assertThat(provider.oldestOwnPaymentDate()).isEmpty();
  }

  @Test
  void fetchUnemailedFirstPaymentsDelegatesToTheRepository() {
    LocalDate windowStart = LocalDate.of(2020, 1, 1);
    LocalDate adultBirthDateCutoff = LocalDate.of(2008, 1, 1);
    FirstThirdPillarPayment payment =
        new FirstThirdPillarPayment(
            "38888888888",
            "First",
            "Last",
            "first.last@example.com",
            "EST",
            BigDecimal.TEN,
            LocalDate.of(2026, 1, 15),
            true,
            false,
            true,
            false,
            false,
            false);
    given(firstPaymentRepository.fetchUnemailedFirstPayments(windowStart, adultBirthDateCutoff))
        .willReturn(List.of(payment));

    assertThat(provider.fetchUnemailedFirstPayments(windowStart, adultBirthDateCutoff))
        .containsExactly(payment);
  }
}
