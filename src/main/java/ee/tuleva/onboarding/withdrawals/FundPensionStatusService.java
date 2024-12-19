package ee.tuleva.onboarding.withdrawals;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FundPensionStatusService {

  private final EpisService episService;

  public FundPensionStatus getFundPensionStatus(Person person) {
    if (PersonalCode.getAge(person.getPersonalCode()) < 55) {
      log.info(
          "Skipping fund pension status query, defaulting to false for under 55: {}",
          person.getPersonalCode());
      return new FundPensionStatus(List.of());
    }

    return FundPensionStatus.from(episService.getFundPensionStatus(person));
  }
}
