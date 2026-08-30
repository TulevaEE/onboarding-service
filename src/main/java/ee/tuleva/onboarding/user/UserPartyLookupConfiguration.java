package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UserPartyLookupConfiguration {

  @Bean
  PartyLookup userPartyLookup(UserRepository userRepository) {
    return new PartyLookup(PartyId.Type.PERSON, userRepository::findByPersonalCode);
  }
}
