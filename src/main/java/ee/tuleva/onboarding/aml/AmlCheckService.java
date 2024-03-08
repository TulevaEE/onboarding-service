package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand;
import ee.tuleva.onboarding.auth.principal.Person;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AmlCheckService {

  private final AmlService amlService;

  public void addCheckIfMissing(Person person, AmlCheckAddCommand command) {
    AmlCheck check =
        AmlCheck.builder()
            .personalCode(person.getPersonalCode())
            .type(command.getType())
            .success(command.isSuccess())
            .metadata(command.getMetadata())
            .build();
    amlService.addCheckIfMissing(check);
  }

  public List<AmlCheckType> getMissingChecks(Person person) {
    val checks = stream(AmlCheckType.values()).filter(AmlCheckType::isManual).collect(toList());
    val existingChecks = amlService.getChecks(person).stream().map(AmlCheck::getType).toList();
    checks.removeAll(existingChecks);
    if (existingChecks.contains(RESIDENCY_AUTO)) {
      checks.remove(RESIDENCY_MANUAL);
    }
    if (!existingChecks.contains(CONTACT_DETAILS)) {
      checks.add(CONTACT_DETAILS);
    }
    if (!existingChecks.contains(POLITICALLY_EXPOSED_PERSON)) {
      checks.remove(POLITICALLY_EXPOSED_PERSON);
    }
    return checks;
  }
}
