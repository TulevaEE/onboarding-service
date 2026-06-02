package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.capital.transfer.iban.IbanValidator;
import ee.tuleva.onboarding.party.PartyId;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IbanWhitelistService {

  private final SavingFundIbanWhitelistRepository repository;

  @Transactional
  public void add(PartyId partyId, String iban, String comment) {
    String canonicalIban = IbanValidator.canonicalize(iban);
    if (repository.existsByPartyTypeAndPartyCodeAndIban(
        partyId.type(), partyId.code(), canonicalIban)) {
      log.info("IBAN already whitelisted, skipping: party={}, iban={}", partyId, canonicalIban);
      return;
    }
    repository.save(
        SavingFundIbanWhitelist.builder()
            .partyType(partyId.type())
            .partyCode(partyId.code())
            .iban(canonicalIban)
            .comment(comment)
            .build());
    log.info("Whitelisted IBAN: party={}, iban={}, comment={}", partyId, canonicalIban, comment);
  }

  @Transactional
  public void remove(PartyId partyId, String iban) {
    String canonicalIban = IbanValidator.canonicalize(iban);
    long removed =
        repository.deleteByPartyTypeAndPartyCodeAndIban(
            partyId.type(), partyId.code(), canonicalIban);
    log.info(
        "Removed whitelisted IBAN: party={}, iban={}, removed={}", partyId, canonicalIban, removed);
  }

  public List<IbanWhitelistEntry> list(PartyId partyId) {
    List<SavingFundIbanWhitelist> entries =
        partyId == null
            ? StreamSupport.stream(repository.findAll().spliterator(), false).toList()
            : repository.findByPartyTypeAndPartyCode(partyId.type(), partyId.code());
    return entries.stream().map(this::toEntry).toList();
  }

  public boolean isWhitelisted(PartyId partyId, String iban) {
    return repository.existsByPartyTypeAndPartyCodeAndIban(
        partyId.type(), partyId.code(), IbanValidator.canonicalize(iban));
  }

  public List<String> findWhitelistedIbans(PartyId partyId) {
    return repository.findByPartyTypeAndPartyCode(partyId.type(), partyId.code()).stream()
        .map(SavingFundIbanWhitelist::getIban)
        .toList();
  }

  private IbanWhitelistEntry toEntry(SavingFundIbanWhitelist entry) {
    return new IbanWhitelistEntry(
        new PartyId(entry.getPartyType(), entry.getPartyCode()),
        entry.getIban(),
        entry.getComment(),
        entry.getCreatedAt());
  }
}
