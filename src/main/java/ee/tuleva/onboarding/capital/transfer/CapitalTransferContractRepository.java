package ee.tuleva.onboarding.capital.transfer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapitalTransferContractRepository
    extends JpaRepository<CapitalTransferContract, Long> {

  List<CapitalTransferContract> findAllBySellerMemberId(Long sellerMemberId);

  List<CapitalTransferContract> findAllByBuyerPersonalCode(String buyerPersonalCode);

  List<CapitalTransferContract> findAllByStatus(CapitalTransferContractStatus status);

  Optional<CapitalTransferContract> findByBuyerPersonalCodeAndStatus(
      String buyerPersonalCode, CapitalTransferContractStatus status);
}
