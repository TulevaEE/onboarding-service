package ee.tuleva.onboarding.populationregister;

import java.time.Duration;
import java.util.List;

public interface PopulationRegisterClient {

  PopulationRegisterPerson fetchPerson(
      String requesterPersonalCode, String personalCode, Duration maxAge);

  List<CustodyRight> fetchCustodyRights(String requesterPersonalCode, Duration maxAge);
}
