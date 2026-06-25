package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NOT_ASSET_MANAGEMENT;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.OTHER;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodyVerificationServiceTest {

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final String OTHER_CHILD = "61001010000";

  @Mock private PopulationRegisterClient populationRegisterClient;

  @InjectMocks private CustodyVerificationService service;

  private final PopulationRegisterPerson aliveChild =
      new PopulationRegisterPerson(
          CHILD, "MARI", "MAASIKAS", LocalDate.of(2015, 6, 15), ALIVE, "EESTI VABARIIK");

  @Test
  void verifiesWhenParentHasAssetManagementCustodyOfAliveChild() {
    given(populationRegisterClient.fetchCustodyRights(PARENT))
        .willReturn(
            List.of(
                new CustodyRight(OTHER_CHILD, PERSONAL, true, true),
                new CustodyRight(CHILD, PROPERTY, true, true)));
    given(populationRegisterClient.fetchPerson(CHILD)).willReturn(aliveChild);

    CustodyVerification result = service.verify(PARENT, CHILD);

    assertThat(result.isVerified()).isTrue();
    assertThat(result.outcome()).isEqualTo(OK);
    assertThat(result.child()).isEqualTo(aliveChild);
    assertThat(result.evidence())
        .containsEntry("custodyType", "PROPERTY")
        .containsEntry("childPersonalCode", CHILD);
  }

  @Test
  void doesNotVerifyAndDoesNotFetchIdentityWhenNoCustodyForThatChild() {
    given(populationRegisterClient.fetchCustodyRights(PARENT))
        .willReturn(List.of(new CustodyRight(OTHER_CHILD, PROPERTY, true, true)));

    CustodyVerification result = service.verify(PARENT, CHILD);

    assertThat(result.outcome()).isEqualTo(NO_CUSTODY);
    assertThat(result.isVerified()).isFalse();
    verify(populationRegisterClient, never()).fetchPerson(any());
  }

  @Test
  void doesNotVerifyWhenCustodyIsOnlyPersonalCare() {
    given(populationRegisterClient.fetchCustodyRights(PARENT))
        .willReturn(List.of(new CustodyRight(CHILD, PERSONAL, true, true)));

    CustodyVerification result = service.verify(PARENT, CHILD);

    assertThat(result.outcome()).isEqualTo(NOT_ASSET_MANAGEMENT);
    verify(populationRegisterClient, never()).fetchPerson(any());
  }

  @Test
  void doesNotVerifyWhenCustodyIsRestrictedOrUnknownType() {
    given(populationRegisterClient.fetchCustodyRights(PARENT))
        .willReturn(List.of(new CustodyRight(CHILD, OTHER, true, true)));

    CustodyVerification result = service.verify(PARENT, CHILD);

    assertThat(result.outcome()).isEqualTo(NOT_ASSET_MANAGEMENT);
    verify(populationRegisterClient, never()).fetchPerson(any());
  }

  @Test
  void doesNotVerifyWhenChildNotAlivePerCustodyRecord() {
    given(populationRegisterClient.fetchCustodyRights(PARENT))
        .willReturn(List.of(new CustodyRight(CHILD, PROPERTY, true, false)));

    CustodyVerification result = service.verify(PARENT, CHILD);

    assertThat(result.outcome()).isEqualTo(CHILD_NOT_ALIVE);
    verify(populationRegisterClient, never()).fetchPerson(any());
  }
}
