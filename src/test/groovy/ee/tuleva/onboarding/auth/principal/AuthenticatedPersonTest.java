package ee.tuleva.onboarding.auth.principal;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.role.Role;
import org.junit.jupiter.api.Test;

class AuthenticatedPersonTest {

  @Test
  void roleIsNullWhenNotSetInBuilder() {
    var person = AuthenticatedPerson.builder().personalCode("38501010002").build();

    assertThat(person.getRole()).isNull();
  }

  @Test
  void roleCanBeSetToCompany() {
    var person = sampleAuthenticatedPersonLegalEntity().build();

    assertThat(person.getRole()).isEqualTo(new Role(LEGAL_ENTITY, "12345678", "Acme OÜ"));
    assertThat(person.getRoleCode()).isEqualTo("12345678");
    assertThat(person.getRoleType()).isEqualTo(LEGAL_ENTITY);
  }

  @Test
  void toStringShowsRoleContext() {
    var person = sampleAuthenticatedPersonAndMember().build();
    assertThat(person.toString()).isEqualTo(person.getPersonalCode());

    var legalEntity = sampleAuthenticatedPersonLegalEntity().build();
    assertThat(legalEntity.toString()).contains(legalEntity.getPersonalCode()).contains("12345678");
  }

  @Test
  void toStringWorksWithoutRole() {
    var person = AuthenticatedPerson.builder().personalCode("1212").build();

    assertThat(person.toString()).isEqualTo("1212");
  }
}
