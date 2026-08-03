package ee.tuleva.onboarding.populationregister;

import java.time.Duration;
import java.util.List;

public interface PopulationRegisterClient {

  PopulationRegisterResult<PopulationRegisterPerson> fetchPerson(
      String requesterPersonalCode, String personalCode, Duration maxAge);

  PopulationRegisterResult<PopulationRegisterPerson> fetchPersonFresh(
      String requesterPersonalCode, String personalCode);

  PopulationRegisterResult<List<CustodyRight>> fetchCustodyRights(
      String requesterPersonalCode, Duration maxAge);

  PopulationRegisterResult<List<CustodyRight>> fetchCustodyRightsFresh(
      String requesterPersonalCode, String parentPersonalCode);

  PopulationRegisterResult<List<Guardian>> fetchGuardians(
      String requesterPersonalCode, String childPersonalCode);
}
