package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.PartyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

interface SavingFundIbanWhitelistRepository extends CrudRepository<SavingFundIbanWhitelist, UUID> {

  boolean existsByPartyTypeAndPartyCodeAndIban(
      PartyId.Type partyType, String partyCode, String iban);

  List<SavingFundIbanWhitelist> findByPartyTypeAndPartyCode(
      PartyId.Type partyType, String partyCode);

  long deleteByPartyTypeAndPartyCodeAndIban(PartyId.Type partyType, String partyCode, String iban);
}
