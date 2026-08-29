package ee.tuleva.onboarding.company;

import ee.tuleva.onboarding.kyb.OnboardedCompanies;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OnboardedCompanyRegistry implements OnboardedCompanies {

  private final CompanyRepository companyRepository;

  @Override
  public List<String> registryCodes() {
    return companyRepository.findAll().stream().map(Company::getRegistryCode).toList();
  }
}
