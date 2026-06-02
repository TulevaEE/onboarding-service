package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.party.PartyId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IbanWhitelistServiceTest {

  @Mock SavingFundIbanWhitelistRepository repository;
  @InjectMocks IbanWhitelistService service;

  private static final PartyId PARTY = new PartyId(PERSON, "39901019992");
  private static final String IBAN = "EE471000001020145685";
  private static final String MESSY_IBAN = "ee47 1000 0010 2014 5685";
  private static final Instant CREATED_AT = Instant.parse("2026-05-29T10:00:00Z");

  @Test
  void add_savesWhenNotAlreadyWhitelisted() {
    given(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN))
        .willReturn(false);

    service.add(PARTY, IBAN, "verified");

    verify(repository).save(any(SavingFundIbanWhitelist.class));
  }

  @Test
  void add_isIdempotentWhenAlreadyWhitelisted() {
    given(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN))
        .willReturn(true);

    service.add(PARTY, IBAN, "verified");

    verify(repository, never()).save(any());
  }

  @Test
  void add_canonicalizesIbanBeforeLookupAndStore() {
    given(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN))
        .willReturn(false);

    service.add(PARTY, MESSY_IBAN, "verified");

    verify(repository).existsByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN);
    verify(repository).save(any(SavingFundIbanWhitelist.class));
  }

  @Test
  void remove_delegatesToRepository() {
    service.remove(PARTY, IBAN);

    verify(repository).deleteByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN);
  }

  @Test
  void remove_canonicalizesIbanBeforeDelete() {
    service.remove(PARTY, MESSY_IBAN);

    verify(repository).deleteByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN);
  }

  @Test
  void isWhitelisted_reflectsRepository() {
    given(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN))
        .willReturn(true);

    assertThat(service.isWhitelisted(PARTY, IBAN)).isTrue();
  }

  @Test
  void isWhitelisted_canonicalizesIbanBeforeLookup() {
    given(repository.existsByPartyTypeAndPartyCodeAndIban(PERSON, PARTY.code(), IBAN))
        .willReturn(true);

    assertThat(service.isWhitelisted(PARTY, MESSY_IBAN)).isTrue();
  }

  @Test
  void findWhitelistedIbans_returnsIbansForParty() {
    given(repository.findByPartyTypeAndPartyCode(PERSON, PARTY.code()))
        .willReturn(List.of(entity(IBAN), entity("EE000000000000000000")));

    assertThat(service.findWhitelistedIbans(PARTY)).containsExactly(IBAN, "EE000000000000000000");
  }

  @Test
  void list_byParty_mapsEntitiesToEntries() {
    given(repository.findByPartyTypeAndPartyCode(PERSON, PARTY.code()))
        .willReturn(List.of(entity(IBAN)));

    assertThat(service.list(PARTY))
        .containsExactly(new IbanWhitelistEntry(PARTY, IBAN, "verified", CREATED_AT));
  }

  @Test
  void list_withoutParty_returnsAll() {
    given(repository.findAll()).willReturn(List.of(entity(IBAN)));

    assertThat(service.list(null))
        .containsExactly(new IbanWhitelistEntry(PARTY, IBAN, "verified", CREATED_AT));
  }

  private SavingFundIbanWhitelist entity(String iban) {
    return SavingFundIbanWhitelist.builder()
        .partyType(PERSON)
        .partyCode(PARTY.code())
        .iban(iban)
        .comment("verified")
        .createdAt(CREATED_AT)
        .build();
  }
}
