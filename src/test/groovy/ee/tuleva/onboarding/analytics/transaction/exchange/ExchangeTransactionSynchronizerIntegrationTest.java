package ee.tuleva.onboarding.analytics.transaction.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder.EpisServiceHolder;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@Transactional
@EnableEpisServiceHolder
class ExchangeTransactionSynchronizerIntegrationTest {

  private static final LocalDate SYNC_REPORTING_DATE = LocalDate.of(2025, 1, 15);

  @Autowired private ExchangeTransactionSynchronizer synchronizer;
  @Autowired private ExchangeTransactionRepository repository;
  @Autowired private EpisServiceHolder episServiceHolder;
  private MockEpisService mockEpisService;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    repository.deleteAll();
    mockEpisService = episServiceHolder.createDelegate(MockEpisService::new);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void sync_deletesExistingForReportingDateAndInsertsNew() {
    // given
    ExchangeTransaction existingTx =
        ExchangeTransaction.builder()
            .reportingDate(SYNC_REPORTING_DATE)
            .securityFrom("OLD_SEC_FROM")
            .securityTo("OLD_SEC_TO")
            .code("OLD_CODE")
            .firstName("Old")
            .name("Transaction")
            .percentage(BigDecimal.TEN)
            .unitAmount(BigDecimal.ONE)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.save(existingTx);
    assertThat(repository.count()).isEqualTo(1);

    List<ExchangeTransactionDto> incomingTxs =
        List.of(
            ExchangeTransactionFixture.Dto.newTransactionDto(),
            ExchangeTransactionFixture.Dto.duplicateTransactionDto());
    mockEpisService.setExchangeTransactions(incomingTxs);

    // when
    synchronizer.sync(SYNC_REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    // then
    List<ExchangeTransaction> all = repository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all.stream().noneMatch(t -> t.getCode().equals("OLD_CODE"))).isTrue();
    assertThat(all).anyMatch(t -> t.getCode().equals(ExchangeTransactionFixture.Dto.NEW_CODE));
    assertThat(all)
        .anyMatch(t -> t.getCode().equals(ExchangeTransactionFixture.Dto.DUPLICATE_CODE));
    assertThat(all)
        .allSatisfy(
            tx -> {
              assertThat(tx.getReportingDate()).isEqualTo(SYNC_REPORTING_DATE);
              assertThat(tx.getDateCreated()).isEqualTo(LocalDateTime.now(TestClockHolder.clock));
            });
  }

  @Test
  void sync_handlesEmptyResponseGracefully() {
    // given
    ExchangeTransaction existingTx =
        ExchangeTransaction.builder()
            .reportingDate(SYNC_REPORTING_DATE.minusDays(1)) // Different date
            .securityFrom("OTHER_SEC_FROM")
            .securityTo("OTHER_SEC_TO")
            .code("OTHER_CODE")
            .firstName("Other")
            .name("Transaction")
            .percentage(BigDecimal.ONE)
            .unitAmount(BigDecimal.TEN)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.save(existingTx);
    assertThat(repository.count()).isEqualTo(1);

    mockEpisService.setExchangeTransactions(Collections.emptyList());

    // when
    synchronizer.sync(SYNC_REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    // then
    List<ExchangeTransaction> all = repository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getCode()).isEqualTo("OTHER_CODE");
  }

  static class MockEpisService extends EpisService {
    private List<ExchangeTransactionDto> exchangeTransactions = new ArrayList<>();

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    @Override
    public List<ExchangeTransactionDto> getExchangeTransactions(
        LocalDate startDate,
        Optional<String> securityFrom,
        Optional<String> securityTo,
        boolean pikFlag) {
      return this.exchangeTransactions;
    }

    public void setExchangeTransactions(List<ExchangeTransactionDto> transactions) {
      this.exchangeTransactions = new ArrayList<>(transactions);
    }
  }
}
