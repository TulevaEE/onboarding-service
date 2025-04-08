package ee.tuleva.onboarding.analytics.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.exchange.ExchangeTransactionFixture.Dto;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
class ExchangeTransactionSynchronizerIntegrationTest {

  private static final LocalDate SYNC_REPORTING_DATE = LocalDate.of(2025, 1, 1);

  private final ExchangeTransactionSynchronizer synchronizer;
  private final ExchangeTransactionRepository repository;
  private final MockEpisService mockEpisService;

  @Autowired
  public ExchangeTransactionSynchronizerIntegrationTest(
      ExchangeTransactionSynchronizer synchronizer,
      ExchangeTransactionRepository repository,
      MockEpisService mockEpisService) {
    this.synchronizer = synchronizer;
    this.repository = repository;
    this.mockEpisService = mockEpisService;
  }

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
  void syncTransactions_deletesExistingForReportingDateAndInsertsNew() {
    // Given: An existing transaction with the same reporting date
    ExchangeTransaction existingTx =
        ExchangeTransaction.builder()
            .reportingDate(SYNC_REPORTING_DATE)
            .securityFrom("OLD_SEC_FROM")
            .securityTo("OLD_SEC_TO")
            .code("OLD_CODE")
            .firstName("Old")
            .name("Transaction")
            .percentage(Dto.duplicateTransactionDto().getPercentage())
            .unitAmount(Dto.duplicateTransactionDto().getUnitAmount())
            .dateCreated(LocalDateTime.now().minusDays(1)) // Different creation time
            .build();
    repository.save(existingTx);
    assertThat(repository.count()).isEqualTo(1);

    List<ExchangeTransactionDto> incomingTxs =
        List.of(Dto.newTransactionDto(), Dto.duplicateTransactionDto()); // Codes NEW_CODE, DUP_CODE
    mockEpisService.setTransactions(incomingTxs);

    // When
    synchronizer.syncTransactions(SYNC_REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    // Then: Existing transaction should be deleted, new ones inserted
    List<ExchangeTransaction> all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all.stream().noneMatch(t -> t.getCode().equals("OLD_CODE"))).isTrue();
    assertThat(all.stream().anyMatch(t -> t.getCode().equals(Dto.NEW_CODE))).isTrue();
    assertThat(all.stream().anyMatch(t -> t.getCode().equals(Dto.DUPLICATE_CODE))).isTrue();
    assertThat(all)
        .allSatisfy(
            tx ->
                assertThat(tx.getDateCreated())
                    .isEqualTo(LocalDateTime.now(TestClockHolder.clock)));
  }

  @Test
  void syncTransactions_handlesEmptyResponseGracefully() {
    // Given
    ExchangeTransaction existingTx =
        ExchangeTransaction.builder()
            .reportingDate(SYNC_REPORTING_DATE.minusDays(1))
            .securityFrom("OTHER_SEC_FROM")
            .securityTo("OTHER_SEC_TO")
            .code("OTHER_CODE")
            .firstName("Other")
            .name("Transaction")
            .percentage(Dto.duplicateTransactionDto().getPercentage())
            .unitAmount(Dto.duplicateTransactionDto().getUnitAmount())
            .dateCreated(LocalDateTime.now().minusDays(1))
            .build();
    repository.save(existingTx);
    assertThat(repository.count()).isEqualTo(1);

    mockEpisService.setTransactions(List.of());

    // When
    synchronizer.syncTransactions(SYNC_REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    // Then
    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findAll().get(0).getCode()).isEqualTo("OTHER_CODE");
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

    private List<ExchangeTransactionDto> transactions = new ArrayList<>();

    public void setTransactions(List<ExchangeTransactionDto> transactions) {
      this.transactions = new ArrayList<>(transactions);
    }

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    @Override
    public List<ExchangeTransactionDto> getExchangeTransactions(
        LocalDate startDate,
        Optional<String> securityFrom,
        Optional<String> securityTo,
        boolean pikFlag) {
      return transactions;
    }

    public void reset() {
      transactions.clear();
    }
  }
}
