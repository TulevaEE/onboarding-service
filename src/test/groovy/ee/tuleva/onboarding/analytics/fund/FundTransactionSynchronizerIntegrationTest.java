package ee.tuleva.onboarding.analytics.fund;

import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.Dto.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundTransactionSynchronizerIntegrationTest {

  private static final String FUND_ISIN = "EE3600019832";
  private static final LocalDate SYNC_START_DATE = LocalDate.of(2025, 4, 1);
  private static final LocalDate SYNC_END_DATE = LocalDate.of(2025, 4, 30);

  @Autowired private FundTransactionSynchronizer synchronizer;
  @Autowired private FundTransactionRepository repository;
  @Autowired private MockEpisService mockEpisService;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    mockEpisService.reset();
  }

  @Test
  void syncTransactions_insertsNewAndSkipsExistingDuplicates() {
    // Given
    FundTransaction existingDuplicate =
        FundTransactionFixture.anotherExampleTransactionBuilder(
                FUND_ISIN, LocalDateTime.now().minusDays(1))
            .build();

    existingDuplicate.setTransactionDate(DUPLICATE_TRANSACTION_DATE);
    existingDuplicate.setPersonalId(DUPLICATE_PERSONAL_ID);
    existingDuplicate.setTransactionType(DUPLICATE_TRANSACTION_TYPE);
    existingDuplicate.setAmount(DUPLICATE_AMOUNT);
    existingDuplicate.setUnitAmount(DUPLICATE_UNIT_AMOUNT);
    repository.save(existingDuplicate);
    long initialCount = repository.count();

    List<FundTransactionDto> incomingTxs = List.of(newTransactionDto(), duplicateTransactionDto());
    mockEpisService.setTransactions(incomingTxs);

    // When
    synchronizer.syncTransactions(FUND_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // Then
    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize((int) initialCount + 1);
    assertThat(all.stream().anyMatch(t -> t.getPersonalId().equals(NEW_PERSONAL_ID))).isTrue();
    assertThat(all.stream().filter(t -> t.getPersonalId().equals(DUPLICATE_PERSONAL_ID)))
        .hasSize(1);

    FundTransaction inserted =
        all.stream()
            .filter(t -> t.getPersonalId().equals(NEW_PERSONAL_ID))
            .findFirst()
            .orElseThrow();
    assertThat(inserted.getIsin()).isEqualTo(FUND_ISIN);
    assertThat(inserted.getDateCreated()).isNotNull();
    assertThat(inserted.getDateCreated())
        .isAfter(LocalDateTime.now(ClockHolder.clock()).minusMinutes(1));
  }

  @Test
  void syncTransactions_handlesEmptyResponseFromEpisGracefully() {
    // Given
    mockEpisService.setTransactions(Collections.emptyList());
    long initialCount = repository.count();

    // When
    synchronizer.syncTransactions(FUND_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // Then
    assertThat(repository.count()).isEqualTo(initialCount);
  }

  @Test
  void syncTransactions_insertsAllWhenRepositoryIsEmpty() {
    // Given
    List<FundTransactionDto> incomingTxs = List.of(newTransactionDto(), duplicateTransactionDto());
    mockEpisService.setTransactions(incomingTxs);
    assertThat(repository.count()).isZero();

    // When
    synchronizer.syncTransactions(FUND_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // Then
    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all.stream().map(FundTransaction::getPersonalId))
        .containsExactlyInAnyOrder(NEW_PERSONAL_ID, DUPLICATE_PERSONAL_ID);
    assertThat(all).allMatch(tx -> FUND_ISIN.equals(tx.getIsin()));
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    public MockEpisService mockEpisService() {
      return new MockEpisService(null, null);
    }
  }

  static class MockEpisService extends EpisService {

    private List<FundTransactionDto> transactions = new ArrayList<>();

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    public void setTransactions(List<FundTransactionDto> transactions) {
      this.transactions = new ArrayList<>(transactions);
    }

    @Override
    public List<FundTransactionDto> getFundTransactions(
        String fundIsin, LocalDate startDate, LocalDate endDate) {
      return transactions;
    }

    public void reset() {
      transactions.clear();
    }
  }
}
