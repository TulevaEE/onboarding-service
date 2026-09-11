package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.aml.SavingsFundCustomers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundCustomerDirectory implements SavingsFundCustomers {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;

  @Override
  public List<String> personalCodes() {
    return savingsFundOnboardingRepository.findPersonCodes();
  }
}
