package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThirdPillarHolderTest {

  private final ThirdPillarHolder customer =
      new ThirdPillarHolder("38888888888", "First", "Last", "EE");

  @Test
  void getPersonalCodeReturnsThePersonalCode() {
    assertThat(customer.getPersonalCode()).isEqualTo("38888888888");
  }

  @Test
  void getFirstNameReturnsTheFirstName() {
    assertThat(customer.getFirstName()).isEqualTo("First");
  }

  @Test
  void getLastNameReturnsTheLastName() {
    assertThat(customer.getLastName()).isEqualTo("Last");
  }
}
