package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.applicationtype.ApplicationType.STOP_CONTRIBUTIONS;
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;
import static ee.tuleva.onboarding.user.SecondPillarExit.MONEY_WITHDRAWN;
import static ee.tuleva.onboarding.user.SecondPillarExit.PAYMENTS_STOPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.MandateGateway;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MandateSecondPillarExitsTest {

  @Mock private MandateGateway mandateGateway;
  @InjectMocks private MandateSecondPillarExits exits;

  private final Person person = samplePerson();

  @Test
  void aRealizedPaymentStopWithoutAWithdrawalMeansThePersonOnlyStoppedPayments() {
    given(mandateGateway.getApplications(person))
        .willReturn(List.of(application(STOP_CONTRIBUTIONS, COMPLETE)));

    assertThat(exits.of(person)).isEqualTo(PAYMENTS_STOPPED);
  }

  @Test
  void aRealizedWithdrawalMeansThePersonWithdrewMoneyEvenWithAPaymentStopThatCanceledARejoin() {
    given(mandateGateway.getApplications(person))
        .willReturn(
            List.of(
                application(EARLY_WITHDRAWAL, COMPLETE),
                application(STOP_CONTRIBUTIONS, COMPLETE)));

    assertThat(exits.of(person)).isEqualTo(MONEY_WITHDRAWN);
  }

  @Test
  void aWithdrawalStillBeingProcessedMeansThePersonWithdrewMoney() {
    given(mandateGateway.getApplications(person))
        .willReturn(
            List.of(
                application(STOP_CONTRIBUTIONS, COMPLETE), application(EARLY_WITHDRAWAL, PENDING)));

    assertThat(exits.of(person)).isEqualTo(MONEY_WITHDRAWN);
  }

  @Test
  void whenTheApplicationListCannotBeReadTheFiveYearRuleForWithdrawalsStillApplies() {
    given(mandateGateway.getApplications(person)).willThrow(new RuntimeException("EPIS down"));

    assertThat(exits.of(person)).isEqualTo(MONEY_WITHDRAWN);
  }

  @Test
  void aPaymentStopStillBeingProcessedDoesNotCount() {
    given(mandateGateway.getApplications(person))
        .willReturn(List.of(application(STOP_CONTRIBUTIONS, PENDING)));

    assertThat(exits.of(person)).isEqualTo(MONEY_WITHDRAWN);
  }

  @Test
  void withNoApplicationOnRecordTheFiveYearRuleForWithdrawalsStillApplies() {
    given(mandateGateway.getApplications(person)).willReturn(List.of());

    assertThat(exits.of(person)).isEqualTo(MONEY_WITHDRAWN);
  }

  private static ApplicationSnapshot application(ApplicationType type, ApplicationStatus status) {
    return ApplicationSnapshot.builder().type(type).status(status).build();
  }
}
