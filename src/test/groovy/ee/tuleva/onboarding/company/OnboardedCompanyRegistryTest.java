package ee.tuleva.onboarding.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.kyb.RegistryCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardedCompanyRegistryTest {

  @Mock private CompanyRepository companyRepository;
  @InjectMocks private OnboardedCompanyRegistry onboardedCompanyRegistry;

  @Test
  void listsTheRegistryCodesOfAllOnboardedCompanies() {
    var first = Company.builder().registryCode("10000001").build();
    var second = Company.builder().registryCode("10000002").build();
    given(companyRepository.findAll()).willReturn(List.of(first, second));

    assertThat(onboardedCompanyRegistry.registryCodes()).containsExactly("10000001", "10000002");
  }

  @Test
  void resolvesTheIdOfAnOnboardedCompany() {
    var companyId = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    var company = Company.builder().id(companyId).registryCode("10000001").build();
    given(companyRepository.findByRegistryCode("10000001")).willReturn(Optional.of(company));

    var result = onboardedCompanyRegistry.resolveId(new RegistryCode("10000001"));

    assertThat(result).isEqualTo(companyId);
  }

  @Test
  void resolvesNullWhenTheCompanyIsNotYetPersisted() {
    given(companyRepository.findByRegistryCode("10000001")).willReturn(Optional.empty());

    var result = onboardedCompanyRegistry.resolveId(new RegistryCode("10000001"));

    assertThat(result).isNull();
  }
}
