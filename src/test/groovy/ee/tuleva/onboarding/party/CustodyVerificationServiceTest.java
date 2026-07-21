package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NOT_ASSET_MANAGEMENT;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.OTHER;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.INVALID;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.INACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.Guardian;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.populationregister.PopulationRegisterResult;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodyVerificationServiceTest {

  private static final Duration MAX_AGE = Duration.ofMinutes(15);

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final String OTHER_CHILD = "61001010000";
  private static final String CO_PARENT = "38002020008";

  private static final UUID CUSTODY_MESSAGE_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CHILD_MESSAGE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock private PopulationRegisterClient populationRegisterClient;

  @InjectMocks private CustodyVerificationService service;

  private final PopulationRegisterPerson aliveChild =
      new PopulationRegisterPerson(
          CHILD, "MARI", "MAASIKAS", LocalDate.of(2015, 6, 15), ALIVE, "EESTI VABARIIK");

  private final PopulationRegisterPerson inactiveChild =
      new PopulationRegisterPerson(
          CHILD, "MARI", "MAASIKAS", LocalDate.of(2015, 6, 15), INACTIVE, "EESTI VABARIIK");

  @Test
  void verifiesWhenParentHasAssetManagementCustodyOfAliveChild() {
    givenCustodyRights(
        new CustodyRight(OTHER_CHILD, PERSONAL_CUSTODY, VALID, ALIVE),
        new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE));
    givenChild(aliveChild);

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.isVerified()).isTrue();
    assertThat(result.outcome()).isEqualTo(OK);
    assertThat(result.child()).isEqualTo(aliveChild);
    assertThat(result.evidence())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "outcome",
                "OK",
                "childPersonalCode",
                CHILD,
                "custodyResponseMessageId",
                CUSTODY_MESSAGE_ID.toString(),
                "childResponseMessageId",
                CHILD_MESSAGE_ID.toString(),
                "custodyType",
                "PROPERTY_CUSTODY",
                "valid",
                true,
                "childAlive",
                true));
  }

  @Test
  void verifiesWhenChildHasBothPersonalAndPropertyCustodyEvenWhenPersonalIsListedFirst() {
    // A full-custody parent has both H10 (personal) and H20 (property) for the same
    // child, and the register lists personal first — verification must still find the
    // property (asset-management) right instead of stopping at the first entry.
    givenCustodyRights(
        new CustodyRight(CHILD, PERSONAL_CUSTODY, VALID, ALIVE),
        new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE));
    givenChild(aliveChild);

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.isVerified()).isTrue();
    assertThat(result.outcome()).isEqualTo(OK);
    assertThat(result.evidence()).containsEntry("custodyType", "PROPERTY_CUSTODY");
  }

  @Test
  void doesNotVerifyAndDoesNotFetchIdentityWhenNoCustodyForThatChild() {
    givenCustodyRights(new CustodyRight(OTHER_CHILD, PROPERTY_CUSTODY, VALID, ALIVE));

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.outcome()).isEqualTo(NO_CUSTODY);
    assertThat(result.isVerified()).isFalse();
    assertThat(result.evidence())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "outcome",
                "NO_CUSTODY",
                "childPersonalCode",
                CHILD,
                "custodyResponseMessageId",
                CUSTODY_MESSAGE_ID.toString()));
    verify(populationRegisterClient, never()).fetchPerson(any(), any(), any());
  }

  @Test
  void doesNotVerifyWhenCustodyIsOnlyPersonalCare() {
    givenCustodyRights(new CustodyRight(CHILD, PERSONAL_CUSTODY, VALID, ALIVE));

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.outcome()).isEqualTo(NOT_ASSET_MANAGEMENT);
    assertThat(result.evidence())
        .containsEntry("custodyResponseMessageId", CUSTODY_MESSAGE_ID.toString())
        .doesNotContainKey("childResponseMessageId");
    verify(populationRegisterClient, never()).fetchPerson(any(), any(), any());
  }

  @Test
  void doesNotVerifyWhenCustodyIsRestrictedOrUnknownType() {
    givenCustodyRights(new CustodyRight(CHILD, OTHER, VALID, ALIVE));

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.outcome()).isEqualTo(NOT_ASSET_MANAGEMENT);
    verify(populationRegisterClient, never()).fetchPerson(any(), any(), any());
  }

  @Test
  void doesNotVerifyWhenChildNotAlivePerCustodyRecord() {
    givenCustodyRights(new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, INACTIVE));

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.outcome()).isEqualTo(CHILD_NOT_ALIVE);
    verify(populationRegisterClient, never()).fetchPerson(any(), any(), any());
  }

  @Test
  void citesBothRegisterResponsesWhenTheIdentityLookupContradictsTheCustodyRecord() {
    givenCustodyRights(new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE));
    givenChild(inactiveChild);

    CustodyVerification result = service.verify(PARENT, CHILD, MAX_AGE);

    assertThat(result.outcome()).isEqualTo(CHILD_NOT_ALIVE);
    assertThat(result.evidence())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "outcome",
                "CHILD_NOT_ALIVE",
                "childPersonalCode",
                CHILD,
                "custodyResponseMessageId",
                CUSTODY_MESSAGE_ID.toString(),
                "childResponseMessageId",
                CHILD_MESSAGE_ID.toString()));
  }

  @Test
  void listsDistinctChildrenWhoseCustodyGrantsAssetManagement() {
    givenCustodyRights(
        new CustodyRight(CHILD, PERSONAL_CUSTODY, VALID, ALIVE),
        new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE),
        new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE),
        new CustodyRight(OTHER_CHILD, PERSONAL_CUSTODY, VALID, ALIVE),
        new CustodyRight("60303030004", PROPERTY_CUSTODY, INVALID, ALIVE),
        new CustodyRight("60404040005", PROPERTY_CUSTODY, VALID, INACTIVE));

    List<CustodyRight> children = service.findChildrenWithAssetManagementCustody(PARENT, MAX_AGE);

    assertThat(children).containsExactly(new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE));
  }

  @Test
  void dedupesEligibleChildrenByPersonalCodeKeepingTheFirstNames() {
    givenCustodyRights(
        new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE, "Mari", "Maasikas"),
        new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE, null, null));

    List<CustodyRight> children = service.findChildrenWithAssetManagementCustody(PARENT, MAX_AGE);

    assertThat(children)
        .containsExactly(
            new CustodyRight(CHILD, PROPERTY_CUSTODY, VALID, ALIVE, "Mari", "Maasikas"));
  }

  @Test
  void
      findGuardiansWithAssetManagement_returnsOtherValidLivingPropertyGuardiansExcludingRequester() {
    given(populationRegisterClient.fetchCustodyRights(PARENT, CHILD))
        .willReturn(
            new PopulationRegisterResult<>(
                List.of(
                    new Guardian(
                        PARENT, PROPERTY_CUSTODY, VALID, ALIVE), // the requester -> excluded
                    new Guardian(CO_PARENT, PROPERTY_CUSTODY, VALID, ALIVE), // kept
                    new Guardian(
                        "60303030004", PERSONAL_CUSTODY, VALID, ALIVE), // personal care only
                    new Guardian(
                        "60404040005", PROPERTY_CUSTODY, INVALID, ALIVE), // custody not valid
                    new Guardian(
                        "60505050006", PROPERTY_CUSTODY, VALID, INACTIVE)), // guardian not alive
                CUSTODY_MESSAGE_ID));

    assertThat(service.findGuardiansWithAssetManagement(CHILD, PARENT)).containsExactly(CO_PARENT);
  }

  @Test
  void findGuardiansWithAssetManagement_returnsEmptyWhenTheOnlyGuardianIsTheRequester() {
    given(populationRegisterClient.fetchCustodyRights(PARENT, CHILD))
        .willReturn(
            new PopulationRegisterResult<>(
                List.of(new Guardian(PARENT, PROPERTY_CUSTODY, VALID, ALIVE)), CUSTODY_MESSAGE_ID));

    assertThat(service.findGuardiansWithAssetManagement(CHILD, PARENT)).isEmpty();
  }

  private void givenCustodyRights(CustodyRight... rights) {
    given(populationRegisterClient.fetchCustodyRights(PARENT, MAX_AGE))
        .willReturn(new PopulationRegisterResult<>(List.of(rights), CUSTODY_MESSAGE_ID));
  }

  private void givenChild(PopulationRegisterPerson child) {
    given(populationRegisterClient.fetchPerson(PARENT, CHILD, MAX_AGE))
        .willReturn(new PopulationRegisterResult<>(child, CHILD_MESSAGE_ID));
  }
}
