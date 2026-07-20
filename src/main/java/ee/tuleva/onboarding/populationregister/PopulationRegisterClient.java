package ee.tuleva.onboarding.populationregister;

import java.time.Duration;
import java.util.List;

public interface PopulationRegisterClient {

  PopulationRegisterResult<PopulationRegisterPerson> fetchPerson(
      String requesterPersonalCode, String personalCode, Duration maxAge);

  PopulationRegisterResult<List<CustodyRight>> fetchCustodyRights(
      String requesterPersonalCode, Duration maxAge);

  // Direction-neutral custody query: the requester asks about a DIFFERENT subject (e.g. a parent
  // asking about their child) and gets that subject's guardians. Unlike the 1-arg overload (which
  // hardwires requester = subject), this must never reuse a cached response across requesters.
  PopulationRegisterResult<List<Guardian>> fetchCustodyRights(
      String requesterPersonalCode, String subjectPersonalCode, Duration maxAge);
}
