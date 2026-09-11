package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PERSONAL_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyRight.Type.PROPERTY_CUSTODY;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.INVALID;
import static ee.tuleva.onboarding.populationregister.CustodyValidity.VALID;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.ALIVE;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterPerson.Status.INACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustodyPredicatesTest {

  @Test
  void custodyRightGrantsAssetManagementOnlyForValidPropertyCustodyOfALivingChild() {
    assertThat(custodyRight(PROPERTY_CUSTODY, VALID, ALIVE).grantsAssetManagement()).isTrue();
    assertThat(custodyRight(PERSONAL_CUSTODY, VALID, ALIVE).grantsAssetManagement()).isFalse();
    assertThat(custodyRight(PROPERTY_CUSTODY, INVALID, ALIVE).grantsAssetManagement()).isFalse();
    assertThat(custodyRight(PROPERTY_CUSTODY, VALID, INACTIVE).grantsAssetManagement()).isFalse();
  }

  @Test
  void custodyRightValidityAndChildStatusPredicates() {
    assertThat(custodyRight(PROPERTY_CUSTODY, VALID, ALIVE).valid()).isTrue();
    assertThat(custodyRight(PROPERTY_CUSTODY, INVALID, ALIVE).valid()).isFalse();
    assertThat(custodyRight(PROPERTY_CUSTODY, VALID, ALIVE).childAlive()).isTrue();
    assertThat(custodyRight(PROPERTY_CUSTODY, VALID, INACTIVE).childAlive()).isFalse();
  }

  @Test
  void guardianGrantsAssetManagementOnlyForValidPropertyCustodyOfALivingGuardian() {
    assertThat(guardian(PROPERTY_CUSTODY, VALID, ALIVE).grantsAssetManagement()).isTrue();
    assertThat(guardian(PERSONAL_CUSTODY, VALID, ALIVE).grantsAssetManagement()).isFalse();
    assertThat(guardian(PROPERTY_CUSTODY, INVALID, ALIVE).grantsAssetManagement()).isFalse();
    assertThat(guardian(PROPERTY_CUSTODY, VALID, INACTIVE).grantsAssetManagement()).isFalse();
  }

  @Test
  void personIsAliveOnlyWithAliveStatus() {
    assertThat(person(ALIVE).isAlive()).isTrue();
    assertThat(person(INACTIVE).isAlive()).isFalse();
  }

  private static CustodyRight custodyRight(
      CustodyRight.Type type,
      CustodyValidity validity,
      PopulationRegisterPerson.Status childStatus) {
    return new CustodyRight("38888888888", type, validity, childStatus);
  }

  private static Guardian guardian(
      CustodyRight.Type type, CustodyValidity validity, PopulationRegisterPerson.Status status) {
    return new Guardian("38888888888", type, validity, status);
  }

  private static PopulationRegisterPerson person(PopulationRegisterPerson.Status status) {
    return new PopulationRegisterPerson(
        "38888888888", "Jaan", "Tamm", null, status, null, java.util.List.of());
  }
}
