package ee.tuleva.onboarding.kyb;

import java.util.List;

public interface KybCheckHistory {

  List<KybCheck> getLatestChecks(PersonalCode personalCode);
}
