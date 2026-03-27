package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PartyResolverTest {

  UserRepository userRepository = mock(UserRepository.class);
  CompanyRepository companyRepository = mock(CompanyRepository.class);
  PartyResolver partyResolver = new PartyResolver(userRepository, companyRepository);

  @Test
  void resolvePerson() {
    var user =
        User.builder().personalCode("37508295796").firstName("PÄRT").lastName("ÕLEKÕRS").build();
    given(userRepository.findByPersonalCode("37508295796")).willReturn(Optional.of(user));

    var result = partyResolver.resolve(new PartyId(PERSON, "37508295796"));

    assertThat(result).contains(user);
    assertThat(result.get().code()).isEqualTo("37508295796");
    assertThat(result.get().name()).isEqualTo("PÄRT ÕLEKÕRS");
  }

  @Test
  void resolvePersonNotFound() {
    given(userRepository.findByPersonalCode("37508295796")).willReturn(Optional.empty());

    var result = partyResolver.resolve(new PartyId(PERSON, "37508295796"));

    assertThat(result).isEmpty();
  }

  @Test
  void resolveLegalEntity() {
    var company = Company.builder().registryCode("12345678").name("Tuleva AS").build();
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.of(company));

    var result = partyResolver.resolve(new PartyId(LEGAL_ENTITY, "12345678"));

    assertThat(result).contains(company);
    assertThat(result.get().code()).isEqualTo("12345678");
    assertThat(result.get().name()).isEqualTo("Tuleva AS");
  }

  @Test
  void resolveLegalEntityNotFound() {
    given(companyRepository.findByRegistryCode("12345678")).willReturn(Optional.empty());

    var result = partyResolver.resolve(new PartyId(LEGAL_ENTITY, "12345678"));

    assertThat(result).isEmpty();
  }
}
