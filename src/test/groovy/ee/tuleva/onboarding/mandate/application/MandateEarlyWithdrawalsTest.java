package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.applicationtype.ApplicationType.WITHDRAWAL;
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.sampleApplication;
import static ee.tuleva.onboarding.mandate.application.ApplicationFixture.withdrawalApplicationDetails;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.auth.principal.Person;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MandateEarlyWithdrawalsTest {

  @Mock private ApplicationService applicationService;
  @InjectMocks private MandateEarlyWithdrawals earlyWithdrawals;

  private final Person person = samplePerson();

  @Test
  void aRealizedEarlyWithdrawalMeansThePersonLeftTheSecondPillar() {
    given(applicationService.getWithdrawalApplications(COMPLETE, person))
        .willReturn(List.of(realized(WITHDRAWAL), realized(EARLY_WITHDRAWAL)));

    assertThat(earlyWithdrawals.hasCompleted(person)).isTrue();
  }

  @Test
  void aRealizedWithdrawalOfAnotherKindDoesNotMeanThePersonLeft() {
    given(applicationService.getWithdrawalApplications(COMPLETE, person))
        .willReturn(List.of(realized(WITHDRAWAL)));

    assertThat(earlyWithdrawals.hasCompleted(person)).isFalse();
  }

  private static Application<WithdrawalApplicationDetails> realized(ApplicationType type) {
    return sampleApplication()
        .status(COMPLETE)
        .details(withdrawalApplicationDetails().type(type).build())
        .build();
  }
}
