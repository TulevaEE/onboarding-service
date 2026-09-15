package ee.tuleva.onboarding.savings.fund.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface UnitTransferRepository extends CrudRepository<UnitTransfer, UUID> {

  List<UnitTransfer> findAllByStateOrderByCreatedAtDesc(UnitTransferState state);
}
