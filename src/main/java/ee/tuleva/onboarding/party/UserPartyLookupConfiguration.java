package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UserPartyLookupConfiguration {

  @Bean
  PartyLookup userPartyLookup(UserRepository userRepository) {
    return new PartyLookup(
        PartyId.Type.PERSON,
        code ->
            userRepository
                .findByPersonalCode(code)
                .map(user -> new ResolvedParty(user.code(), user.name())));
  }
}
