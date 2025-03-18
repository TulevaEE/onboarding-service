package ee.tuleva.onboarding.analytics.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.exchange.ExchangeTransactionFixture.Dto;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

  private static final LocalDate SYNC_START_DATE = LocalDate.of(2025, 1, 1);

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
    repository.deleteAll();
    mockEpisService.reset();
  }

  @Test
  void syncTransactions_insertsNewAndSkipsDuplicates() {
    // Given
    ExchangeTransaction existingDuplicate =
        ExchangeTransaction.builder()
            .reportingDate(SYNC_START_DATE)
            .securityFrom(Dto.DUPLICATE_SECURITY_FROM)
            .securityTo(Dto.DUPLICATE_SECURITY_TO)
            .fundManagerFrom("FUND_MGR_FROM_DUP")
            .fundManagerTo("FUND_MGR_TO_DUP")
            .code(Dto.DUPLICATE_CODE)
            .firstName("Jane")
            .name("Duplicate")
            .percentage(Dto.duplicateTransactionDto().getPercentage())
            .unitAmount(Dto.duplicateTransactionDto().getUnitAmount())
            .dateCreated(java.time.LocalDateTime.now())
            .build();
    repository.save(existingDuplicate);

    List<ExchangeTransactionDto> incomingTxs =
        List.of(Dto.newTransactionDto(), Dto.duplicateTransactionDto());
    mockEpisService.setTransactions(incomingTxs);

    // When
    synchronizer.syncTransactions(SYNC_START_DATE, Optional.empty(), Optional.empty(), false);

    // Then
    List<ExchangeTransaction> all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all.stream().anyMatch(t -> t.getCode().equals(Dto.NEW_CODE))).isTrue();
    assertThat(all.stream().filter(t -> t.getCode().equals(Dto.DUPLICATE_CODE))).hasSize(1);
  }

  @Test
  void syncTransactions_handlesEmptyResponseGracefully() {
    // Given
    mockEpisService.setTransactions(List.of());

    // When
    synchronizer.syncTransactions(SYNC_START_DATE, Optional.empty(), Optional.empty(), false);

    // Then
    assertThat(repository.findAll()).isEmpty();
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    public MockEpisService mockEpisService() {
      return new MockEpisService(null, null);
    }

    @Bean
    public ExchangeTransactionSynchronizer exchangeTransactionSynchronizer(
        MockEpisService episService, ExchangeTransactionRepository exchangeTransactionRepository) {
      return new ExchangeTransactionSynchronizer(episService, exchangeTransactionRepository);
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
