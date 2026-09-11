package ee.tuleva.onboarding.user.member;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import org.junit.jupiter.api.Test;

class MemberTest {

  private static final User user =
      User.builder().personalCode("38888888888").firstName("John").lastName("Doe").build();

  private static final Member member = Member.builder().user(user).memberNumber(1001).build();

  @Test
  void getFirstNameDelegatesToUser() {
    assertThat(member.getFirstName()).isEqualTo("John");
  }

  @Test
  void getLastNameDelegatesToUser() {
    assertThat(member.getLastName()).isEqualTo("Doe");
  }

  @Test
  void getFullNameDelegatesToUser() {
    assertThat(member.getFullName()).isEqualTo("John Doe");
  }
}
