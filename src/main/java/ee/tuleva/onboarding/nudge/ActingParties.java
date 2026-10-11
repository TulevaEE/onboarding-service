package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import ee.tuleva.onboarding.auth.role.CompanyRoles;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ActingParties {

  private final ChildRepresentations childRepresentations;
  private final CompanyRoles companyRoles;

  List<NudgeAccount> representedBy(String personalCode) {
    var parties = new ArrayList<NudgeAccount>();
    childRepresentations
        .findActivelyRepresentedChildren(personalCode)
        .keySet()
        .forEach(childCode -> parties.add(NudgeAccount.person(childCode)));
    companyRoles
        .boardMemberCompanies(personalCode)
        .forEach(company -> parties.add(NudgeAccount.company(company.registryCode())));
    return List.copyOf(parties);
  }
}
