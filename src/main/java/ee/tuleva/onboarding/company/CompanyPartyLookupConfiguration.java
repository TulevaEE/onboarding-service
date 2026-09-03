package ee.tuleva.onboarding.company;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CompanyPartyLookupConfiguration {

  @Bean
  PartyLookup companyPartyLookup(CompanyRepository companyRepository) {
    return new PartyLookup(PartyId.Type.LEGAL_ENTITY, companyRepository::findByRegistryCode);
  }
}
