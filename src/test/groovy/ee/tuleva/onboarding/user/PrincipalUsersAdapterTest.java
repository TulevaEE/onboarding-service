package ee.tuleva.onboarding.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.PersonFixture;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PrincipalUsers.PrincipalUser;
import ee.tuleva.onboarding.user.member.Member;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrincipalUsersAdapterTest {

  @Mock private UserService userService;

  @InjectMocks private PrincipalUsersAdapter principalUsersAdapter;

  @Test
  void findOrCreateReturnsTheExistingUser() {
    Person person = PersonFixture.samplePerson();
    User existingUser =
        User.builder().id(1L).personalCode(person.getPersonalCode()).active(true).build();
    given(userService.findByPersonalCode(person.getPersonalCode()))
        .willReturn(Optional.of(existingUser));

    PrincipalUser result = principalUsersAdapter.findOrCreate(person);

    assertThat(result).isEqualTo(new PrincipalUser(1L, true));
  }

  @Test
  void findOrCreateCreatesAUserWhenOneIsNotPresent() {
    Person person =
        PersonFixture.samplePerson().toBuilder().firstName("JOHN").lastName("DOE").build();
    User expectedNewUser =
        User.builder()
            .firstName("John")
            .lastName("Doe")
            .personalCode(person.getPersonalCode())
            .active(true)
            .build();
    given(userService.findByPersonalCode(person.getPersonalCode())).willReturn(Optional.empty());
    User createdUser = User.builder().id(2L).active(true).build();
    given(userService.createNewUser(expectedNewUser)).willReturn(createdUser);

    PrincipalUser result = principalUsersAdapter.findOrCreate(person);

    verify(userService).createNewUser(expectedNewUser);
    assertThat(result).isEqualTo(new PrincipalUser(2L, true));
  }

  @Test
  void isMemberReturnsTrueWhenTheUserHasAMember() {
    User user = User.builder().id(1L).member(Member.builder().build()).build();
    given(userService.getById(1L)).willReturn(Optional.of(user));

    assertThat(principalUsersAdapter.isMember(1L)).isTrue();
  }

  @Test
  void isMemberReturnsFalseWhenTheUserHasNoMember() {
    User user = User.builder().id(1L).build();
    given(userService.getById(1L)).willReturn(Optional.of(user));

    assertThat(principalUsersAdapter.isMember(1L)).isFalse();
  }

  @Test
  void fullNameReturnsTheUsersFullNameWhenPresent() {
    User user =
        User.builder().personalCode("38812121215").firstName("Jordan").lastName("Valdma").build();
    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.of(user));

    assertThat(principalUsersAdapter.fullName("38812121215")).contains("Jordan Valdma");
  }

  @Test
  void fullNameIsEmptyWhenNoUserIsFound() {
    given(userService.findByPersonalCode("38812121215")).willReturn(Optional.empty());

    assertThat(principalUsersAdapter.fullName("38812121215")).isEmpty();
  }
}
