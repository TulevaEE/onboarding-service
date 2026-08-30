package ee.tuleva.onboarding.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyLookup;
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
  void findsUsersByPersonalCode() {
    User user = User.builder().personalCode("38888888888").build();
    given(userRepository.findByPersonalCode("38888888888")).willReturn(Optional.of(user));

    assertThat(lookup.find("38888888888")).contains(user);
  }
}
