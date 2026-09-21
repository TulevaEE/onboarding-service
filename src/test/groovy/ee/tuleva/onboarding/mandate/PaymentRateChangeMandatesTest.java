package ee.tuleva.onboarding.mandate;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.SIX;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.details.WithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRateChangeMandatesTest {

  private static final Instant SINCE = Instant.parse("2025-11-30T21:59:59.999999999Z");

  @Mock private MandateRepository mandateRepository;
  @InjectMocks private PaymentRateChangeMandates paymentRateChangeMandates;

  private final User user = sampleUser().build();

  @Test
  void aRateChangeSignedInThePeriodCounts() {
    given(mandateRepository.findAllByUserIdAndCreatedDateAfter(user.getId(), SINCE))
        .willReturn(List.of(signed(new PaymentRateChangeMandateDetails(SIX))));

    assertThat(paymentRateChangeMandates.hasChangeSince(user, SINCE)).isTrue();
  }

  @Test
  void aRateChangeTheSaverStartedButNeverSignedIsNotAChange() {
    given(mandateRepository.findAllByUserIdAndCreatedDateAfter(user.getId(), SINCE))
        .willReturn(List.of(unsigned(new PaymentRateChangeMandateDetails(SIX))));

    assertThat(paymentRateChangeMandates.hasChangeSince(user, SINCE)).isFalse();
  }

  @Test
  void anotherKindOfMandateInThePeriodDoesNotCount() {
    given(mandateRepository.findAllByUserIdAndCreatedDateAfter(user.getId(), SINCE))
        .willReturn(List.of(signed(new WithdrawalCancellationMandateDetails())));

    assertThat(paymentRateChangeMandates.hasChangeSince(user, SINCE)).isFalse();
  }

  @Test
  void aPersonWithNoMandatesInThePeriodHasNotChangedTheirRate() {
    given(mandateRepository.findAllByUserIdAndCreatedDateAfter(user.getId(), SINCE))
        .willReturn(List.of());

    assertThat(paymentRateChangeMandates.hasChangeSince(user, SINCE)).isFalse();
  }

  private Mandate signed(MandateDetails details) {
    Mandate mandate = unsigned(details);
    mandate.setMandate("signed".getBytes(UTF_8));
    return mandate;
  }

  private Mandate unsigned(MandateDetails details) {
    return Mandate.builder().user(user).pillar(2).details(details).build();
  }
}
