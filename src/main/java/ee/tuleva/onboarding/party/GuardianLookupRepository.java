package ee.tuleva.onboarding.party;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GuardianLookupRepository extends JpaRepository<GuardianLookup, String> {

  @Query("SELECT lookup.childPersonalCode FROM GuardianLookup lookup")
  List<String> findAllChildPersonalCodes();
}
