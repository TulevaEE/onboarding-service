package ee.tuleva.onboarding.auth.principal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonTest {

  @Test
  void defaultRepresentedPersonalCodeIsTheirOwnPersonalCode() {
    Person person =
        PersonImpl.builder()
            .personalCode("38888888888")
            .firstName("Aadu")
            .lastName("Kadakas")
            .build();

    assertThat(person.getRepresentedPersonalCode()).isEqualTo("38888888888");
  }
}
