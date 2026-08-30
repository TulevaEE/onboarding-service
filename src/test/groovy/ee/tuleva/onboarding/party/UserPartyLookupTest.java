package ee.tuleva.onboarding.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserPartyLookupTest {

  UserRepository userRepository = mock(UserRepository.class);
  PartyLookup lookup = new UserPartyLookupConfiguration().userPartyLookup(userRepository);

  @Test
  void supportsPersons() {
    assertThat(lookup.type()).isEqualTo(PartyId.Type.PERSON);
  }

  @Test
  void resolvesUsersToPartiesByPersonalCode() {
    User user =
        User.builder().personalCode("38888888888").firstName("Jordan").lastName("Tester").build();
    given(userRepository.findByPersonalCode("38888888888")).willReturn(Optional.of(user));

    assertThat(lookup.find("38888888888")).contains(new ResolvedParty("38888888888", user.name()));
  }
}
