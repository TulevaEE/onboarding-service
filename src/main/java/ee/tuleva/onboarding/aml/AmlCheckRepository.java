package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlCheckRepository extends JpaRepository<AmlCheck, Long> {

  boolean existsByUserAndType(User user, AmlCheckType type);

  List<AmlCheck> findAllByUser(User user);
}
