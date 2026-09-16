package ee.tuleva.onboarding.savings.fund.gift;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftLinkRepository extends JpaRepository<GiftLink, UUID> {

  Optional<GiftLink> findByTokenAndClosedAtIsNull(String token);

  Optional<GiftLink> findByRecipientPersonalCodeAndClosedAtIsNull(String recipientPersonalCode);
}
