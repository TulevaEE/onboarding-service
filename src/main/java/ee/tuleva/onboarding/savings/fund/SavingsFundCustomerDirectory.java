package ee.tuleva.onboarding.savings.fund;

import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.aml.SavingsFundCustomers;
import ee.tuleva.onboarding.aml.SavingsFundGuardian;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundCustomerDirectory implements SavingsFundCustomers {

  private final SavingsFundOnboardingRepository savingsFundOnboardingRepository;
  private final ParentChildLinkService parentChildLinkService;

  @Override
  public List<String> personalCodes() {
    return savingsFundOnboardingRepository.findPersonCodes();
  }

  @Override
  public List<SavingsFundGuardian> guardians() {
    Set<String> holders = Set.copyOf(personalCodes());
    return List.copyOf(
        parentChildLinkService.findCurrentRepresentatives().stream()
            .filter(link -> holders.contains(link.getChildPersonalCode()))
            .map(SavingsFundCustomerDirectory::guardianOf)
            .collect(
                toMap(
                    SavingsFundGuardian::personalCode,
                    guardian -> guardian,
                    SavingsFundCustomerDirectory::preferRecordedName,
                    LinkedHashMap::new))
            .values());
  }

  private static SavingsFundGuardian guardianOf(ParentChildLink link) {
    return new SavingsFundGuardian(
        link.getParentPersonalCode(), link.getParentFirstName(), link.getParentLastName());
  }

  private static SavingsFundGuardian preferRecordedName(
      SavingsFundGuardian first, SavingsFundGuardian second) {
    return first.hasRecordedName() ? first : second;
  }
}
