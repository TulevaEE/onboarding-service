package ee.tuleva.onboarding.party;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentChildLinkRepository extends JpaRepository<ParentChildLink, UUID> {

  List<ParentChildLink> findByParentPersonalCodeAndValidUntilAfter(
      String parentPersonalCode, LocalDate date);

  boolean existsByParentPersonalCodeAndChildPersonalCodeAndValidUntilAfter(
      String parentPersonalCode, String childPersonalCode, LocalDate date);
}
