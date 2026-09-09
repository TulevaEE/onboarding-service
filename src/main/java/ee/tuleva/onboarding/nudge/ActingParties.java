package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import ee.tuleva.onboarding.auth.role.CompanyRoles;
import ee.tuleva.onboarding.user.User;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ActingParties {

  private final ChildRepresentations childRepresentations;
  private final CompanyRoles companyRoles;

  List<NudgeAccount> representedBy(User user) {
    var parties = new ArrayList<NudgeAccount>();
    childRepresentations
        .findActivelyRepresentedChildren(user.getPersonalCode())
        .keySet()
        .forEach(childCode -> parties.add(NudgeAccount.person(childCode)));
    companyRoles
        .boardMemberCompanies(user.getPersonalCode())
        .forEach(company -> parties.add(NudgeAccount.company(company.registryCode())));
    return List.copyOf(parties);
  }
}
