package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class LedgerPartyServiceTest {

  @Mock private LedgerPartyRepository ledgerPartyRepository;
  @Mock private JdbcClient jdbcClient;
  @Mock private JdbcClient.StatementSpec statementSpec;
  @Mock private JdbcClient.MappedQuerySpec<Object> mappedQuerySpec;
  @InjectMocks private LedgerPartyService ledgerPartyService;

  final String ownerId = "38812121215";
  final LedgerParty existingParty =
      LedgerParty.builder().partyType(PERSON).ownerId(ownerId).details(Map.of()).build();

  @Test
  void getOrCreate_returnsExistingParty() {
    given(ledgerPartyRepository.findByOwnerIdAndPartyType(ownerId, PERSON))
        .willReturn(existingParty);

    assertThat(ledgerPartyService.getOrCreate(ownerId, PERSON)).isEqualTo(existingParty);

    verify(ledgerPartyRepository, never()).save(any());
  }

  @Test
  void getOrCreate_createsPartyWhenNotFound() {
    given(ledgerPartyRepository.findByOwnerIdAndPartyType(ownerId, PERSON)).willReturn(null);
    given(ledgerPartyRepository.save(any())).willReturn(existingParty);
    stubAdvisoryLock();

    assertThat(ledgerPartyService.getOrCreate(ownerId, PERSON)).isEqualTo(existingParty);

    verify(ledgerPartyRepository).save(any());
  }

  @Test
  void getOrCreate_returnsExistingPartyAfterLockWhenCreatedByConcurrentThread() {
    given(ledgerPartyRepository.findByOwnerIdAndPartyType(ownerId, PERSON))
        .willReturn(null)
        .willReturn(existingParty);
    stubAdvisoryLock();

    assertThat(ledgerPartyService.getOrCreate(ownerId, PERSON)).isEqualTo(existingParty);

    verify(ledgerPartyRepository, never()).save(any());
  }

  @SuppressWarnings("unchecked")
  private void stubAdvisoryLock() {
    given(jdbcClient.sql(contains("pg_advisory_xact_lock"))).willReturn(statementSpec);
    given(statementSpec.param(eq("key"), anyLong())).willReturn(statementSpec);
    given(statementSpec.query(any(org.springframework.jdbc.core.RowMapper.class)))
        .willReturn(mappedQuerySpec);
    given(mappedQuerySpec.optional()).willReturn(Optional.empty());
  }
}
