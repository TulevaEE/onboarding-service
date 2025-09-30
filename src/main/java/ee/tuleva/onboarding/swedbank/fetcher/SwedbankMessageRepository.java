package ee.tuleva.onboarding.swedbank.fetcher;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
interface SwedbankMessageRepository extends CrudRepository<SwedbankMessage, UUID> {}
