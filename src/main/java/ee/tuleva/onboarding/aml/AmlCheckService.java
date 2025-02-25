package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.time.ClockHolder.aYearAgo;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AmlCheckService {

  private final AmlService amlService;
  private final EpisService episService;

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
    final var doneChecksTypes =
        amlService.getChecks(person).stream().map(AmlCheck::getType).toList();

    final var missingCheckTypes =
        stream(AmlCheckType.values())
            .filter(AmlCheckType::isManual)
            .filter(value -> !doneChecksTypes.contains(value))
            .collect(toList());

    if (doneChecksTypes.contains(RESIDENCY_AUTO)) {
      missingCheckTypes.remove(RESIDENCY_MANUAL);
    }
    if (!doneChecksTypes.contains(CONTACT_DETAILS) || isAddressCheckNeededForEpis(person)) {
      missingCheckTypes.add(CONTACT_DETAILS);
    }
    if (!doneChecksTypes.contains(POLITICALLY_EXPOSED_PERSON)) {
      missingCheckTypes.remove(POLITICALLY_EXPOSED_PERSON);
    }
    return missingCheckTypes;
  }

  private boolean isAddressCheckNeededForEpis(Person person) {
    var episContactDetails = episService.getContactDetails(person);

    if (episContactDetails.getLastUpdateDate() == null) {
      return true;
    }

    return episContactDetails.getLastUpdateDate().isBefore(aYearAgo());
  }
}
