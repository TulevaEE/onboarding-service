package ee.tuleva.onboarding.populationregister;

import java.util.List;

public interface PopulationRegisterClient {

  PopulationRegisterPerson fetchPerson(String personalCode);

  List<CustodyRight> fetchCustodyRights(String personalCode);
}
