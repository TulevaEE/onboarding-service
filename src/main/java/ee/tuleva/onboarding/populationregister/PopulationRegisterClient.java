package ee.tuleva.onboarding.populationregister;

import java.time.Duration;
import java.util.List;

public interface PopulationRegisterClient {

  PopulationRegisterResult<PopulationRegisterPerson> fetchPerson(
      String requesterPersonalCode, String personalCode, Duration maxAge);

  PopulationRegisterResult<List<CustodyRight>> fetchCustodyRights(
      String requesterPersonalCode, Duration maxAge);
}
