package ee.tuleva.onboarding.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
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
}
