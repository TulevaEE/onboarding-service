package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlCheckRepository extends JpaRepository<AmlCheck, Long> {

    public boolean existsByUserAndType(User user, AmlCheckType type);
}
