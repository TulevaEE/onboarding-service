package ee.tuleva.onboarding.auth.session;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionAttributeRepository extends JpaRepository<SessionAttribute, Long> {
  Optional<SessionAttribute> findByUserIdAndAttributeName(Long userId, String attributeName);
}
