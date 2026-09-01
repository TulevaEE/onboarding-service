package ee.tuleva.onboarding.company;

import ee.tuleva.onboarding.kyb.CompanyIdResolver;
import ee.tuleva.onboarding.kyb.OnboardedCompanies;
import ee.tuleva.onboarding.kyb.RegistryCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OnboardedCompanyRegistry implements OnboardedCompanies, CompanyIdResolver {

  private final CompanyRepository companyRepository;

  @Override
  public List<String> registryCodes() {
    return companyRepository.findAll().stream().map(Company::getRegistryCode).toList();
  }

  @Override
  public @Nullable UUID resolveId(RegistryCode registryCode) {
    return companyRepository
        .findByRegistryCode(registryCode.value())
        .map(Company::getId)
        .orElse(null);
  }
}
