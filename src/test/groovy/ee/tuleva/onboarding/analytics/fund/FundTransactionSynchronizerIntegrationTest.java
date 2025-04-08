package ee.tuleva.onboarding.analytics.fund;

import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.Dto.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder; // Import test clock
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
  private static final String OTHER_FUND_ISIN = "EE3600109435";
  private static final LocalDate SYNC_START_DATE = LocalDate.of(2025, 4, 1);
  private static final LocalDate SYNC_END_DATE = LocalDate.of(2025, 4, 30);
  private static final LocalDate DATE_WITHIN_RANGE = LocalDate.of(2025, 4, 15);
  private static final LocalDate DATE_BEFORE_RANGE = LocalDate.of(2025, 3, 31);
  private static final LocalDate DATE_AFTER_RANGE = LocalDate.of(2025, 5, 1);

  @Autowired private FundTransactionSynchronizer synchronizer;
  @Autowired private FundTransactionRepository repository;
  @Autowired private MockEpisService mockEpisService;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    repository.deleteAll();
    mockEpisService.reset();
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void syncTransactions_deletesExistingInRangeForCorrectIsin_InsertsAllFetched() {
    // Given
    FundTransaction txToDelete = createTestTransaction(FUND_ISIN, DATE_WITHIN_RANGE, "P1", "T1");
    FundTransaction txToKeepBefore =
        createTestTransaction(FUND_ISIN, DATE_BEFORE_RANGE, "P2", "T2");
    FundTransaction txToKeepAfter = createTestTransaction(FUND_ISIN, DATE_AFTER_RANGE, "P3", "T3");
    FundTransaction txToKeepOtherIsin =
        createTestTransaction(OTHER_FUND_ISIN, DATE_WITHIN_RANGE, "P4", "T4");

    repository.saveAll(List.of(txToDelete, txToKeepBefore, txToKeepAfter, txToKeepOtherIsin));
    long initialCount = repository.count();
    assertThat(initialCount).isEqualTo(4);

    FundTransactionDto incomingDto1 = newTransactionDto();
    incomingDto1.setDate(DATE_WITHIN_RANGE);
    FundTransactionDto incomingDto2 = duplicateTransactionDto();
    incomingDto2.setDate(DATE_WITHIN_RANGE.plusDays(1));

    List<FundTransactionDto> incomingTxs = List.of(incomingDto1, incomingDto2);
    mockEpisService.setTransactions(incomingTxs);

    // When
    synchronizer.syncTransactions(FUND_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // Then
    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize(5);

    assertThat(all.stream().noneMatch(t -> t.getPersonalId().equals("P1"))).isTrue();

    assertThat(
            all.stream()
                .anyMatch(t -> t.getPersonalId().equals("P2") && t.getIsin().equals(FUND_ISIN)))
        .isTrue();
    assertThat(
            all.stream()
                .anyMatch(t -> t.getPersonalId().equals("P3") && t.getIsin().equals(FUND_ISIN)))
        .isTrue();
    assertThat(
            all.stream()
                .anyMatch(
                    t -> t.getPersonalId().equals("P4") && t.getIsin().equals(OTHER_FUND_ISIN)))
        .isTrue();

    assertThat(
            all.stream()
                .anyMatch(
                    t ->
                        t.getPersonalId().equals(NEW_PERSONAL_ID) && t.getIsin().equals(FUND_ISIN)))
        .isTrue();
    assertThat(
            all.stream()
                .anyMatch(
                    t ->
                        t.getPersonalId().equals(DUPLICATE_PERSONAL_ID)
                            && t.getIsin().equals(FUND_ISIN)))
        .isTrue();

    LocalDateTime now = LocalDateTime.now(ClockHolder.clock());
    assertThat(all)
        .filteredOn(t -> t.getPersonalId().equals(NEW_PERSONAL_ID))
        .extracting(FundTransaction::getDateCreated)
        .allMatch(dt -> dt.isEqual(now) || dt.isAfter(now.minusSeconds(5)));
    assertThat(all)
        .filteredOn(t -> t.getPersonalId().equals(DUPLICATE_PERSONAL_ID))
        .extracting(FundTransaction::getDateCreated)
        .allMatch(dt -> dt.isEqual(now) || dt.isAfter(now.minusSeconds(5)));
  }

  @Test
  void syncTransactions_handlesEmptyResponseFromEpisGracefully_doesNotDelete() {
    // Given
    FundTransaction txExisting = createTestTransaction(FUND_ISIN, DATE_WITHIN_RANGE, "P1", "T1");
    repository.save(txExisting);
    long initialCount = repository.count();
    assertThat(initialCount).isEqualTo(1);

    mockEpisService.setTransactions(Collections.emptyList());

    // When
    synchronizer.syncTransactions(FUND_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // Then
    assertThat(repository.count()).isEqualTo(initialCount);
    assertThat(repository.findById(txExisting.getId())).isPresent();
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
    assertThat(all).allMatch(tx -> tx.getDateCreated() != null);
  }

  private FundTransaction createTestTransaction(
      String isin, LocalDate date, String personalId, String txType) {
    return FundTransaction.builder()
        .isin(isin)
        .transactionDate(date)
        .personalId(personalId)
        .transactionType(txType)
        .amount(BigDecimal.TEN)
        .unitAmount(BigDecimal.ONE)
        .dateCreated(LocalDateTime.now().minusDays(1))
        .build();
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
      if (FUND_ISIN.equals(fundIsin)) {
        return new ArrayList<>(transactions);
      } else {
        return Collections.emptyList();
      }
    }

    public void reset() {
      transactions.clear();
    }
  }
}
