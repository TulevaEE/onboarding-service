package ee.tuleva.onboarding.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyLookup;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanyPartyLookupTest {

  CompanyRepository companyRepository = mock(CompanyRepository.class);
  PartyLookup lookup = new CompanyPartyLookupConfiguration().companyPartyLookup(companyRepository);

  @Test
  void supportsLegalEntities() {
    assertThat(lookup.type()).isEqualTo(PartyId.Type.LEGAL_ENTITY);
  }

  @Test
  void findsCompaniesByRegistryCode() {
    Company company = Company.builder().registryCode("12345678").build();
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.of(company));

    assertThat(lookup.find("12345678")).contains(company);
  }
}
