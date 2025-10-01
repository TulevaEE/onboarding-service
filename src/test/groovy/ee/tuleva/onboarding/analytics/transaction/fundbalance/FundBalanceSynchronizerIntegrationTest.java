package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder.EpisServiceHolder;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.*;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
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
class FundBalanceSynchronizerIntegrationTest {

  private static final LocalDate SYNC_DATE = LocalDate.of(2025, 4, 21);
  private static final LocalDate OTHER_DATE = LocalDate.of(2025, 4, 20);

  @Autowired private FundBalanceSynchronizer synchronizer;
  @Autowired private FundBalanceRepository repository;
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
  void sync_deletesExistingForDateAndInsertsNew() {
    // given
    FundBalance existingOnSyncDate =
        entityBuilder(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .isin("OLD_ISIN_SYNC_DATE")
            .requestDate(SYNC_DATE)
            .build();
    FundBalance existingOnOtherDate =
        entityBuilder(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .isin("OLD_ISIN_OTHER_DATE")
            .requestDate(OTHER_DATE)
            .build();
    repository.saveAll(List.of(existingOnSyncDate, existingOnOtherDate));
    assertThat(repository.count()).isEqualTo(2);

    List<TransactionFundBalanceDto> incoming =
        List.of(
            dtoBuilder().isin(ISIN_1).requestDate(SYNC_DATE).build(),
            dtoBuilder().isin(ISIN_2).requestDate(SYNC_DATE).build());
    mockEpisService.setFundBalances(incoming);

    // when
    synchronizer.sync(SYNC_DATE);

    // then
    List<FundBalance> all = repository.findAll();
    assertThat(all).hasSize(3); // 1 old (other date) + 2 new

    assertThat(all).noneMatch(fb -> fb.getIsin().equals("OLD_ISIN_SYNC_DATE"));
    assertThat(all).anyMatch(fb -> fb.getIsin().equals("OLD_ISIN_OTHER_DATE"));
    assertThat(all)
        .anyMatch(fb -> fb.getIsin().equals(ISIN_1) && fb.getRequestDate().equals(SYNC_DATE));
    assertThat(all)
        .anyMatch(fb -> fb.getIsin().equals(ISIN_2) && fb.getRequestDate().equals(SYNC_DATE));
    assertThat(all)
        .filteredOn(fb -> fb.getRequestDate().equals(SYNC_DATE))
        .allSatisfy(
            fb ->
                assertThat(fb.getDateCreated())
                    .isEqualTo(LocalDateTime.now(TestClockHolder.clock)));
  }

  @Test
  void sync_handlesEmptyResponseGracefully() {
    // given
    FundBalance existing =
        entityBuilder(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .isin("EXISTING_ISIN")
            .requestDate(SYNC_DATE)
            .build();
    repository.save(existing);
    assertThat(repository.count()).isEqualTo(1);

    mockEpisService.setFundBalances(Collections.emptyList());

    // when
    synchronizer.sync(SYNC_DATE);

    // then
    List<FundBalance> all = repository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getIsin()).isEqualTo("EXISTING_ISIN");
  }

  static class MockEpisService extends EpisService {
    private List<TransactionFundBalanceDto> fundBalances = new ArrayList<>();

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    @Override
    public List<TransactionFundBalanceDto> getFundBalances(LocalDate requestDate) {
      // Return only balances matching the requested date
      return this.fundBalances.stream()
          .filter(fb -> fb.getRequestDate().equals(requestDate))
          .toList();
    }

    @Override
    public List<FundTransactionDto> getFundTransactions(
        String isin, LocalDate fromDate, LocalDate toDate) {
      return Collections.emptyList();
    }

    @Override
    public List<ThirdPillarTransactionDto> getTransactions(LocalDate startDate, LocalDate endDate) {
      return Collections.emptyList();
    }

    @Override
    public List<ExchangeTransactionDto> getExchangeTransactions(
        LocalDate startDate,
        Optional<String> securityFrom,
        Optional<String> securityTo,
        boolean pikFlag) {
      return Collections.emptyList();
    }

    @Override
    public List<UnitOwnerDto> getUnitOwners() {
      return Collections.emptyList();
    }

    public void setFundBalances(List<TransactionFundBalanceDto> balances) {
      this.fundBalances = new ArrayList<>(balances);
    }
  }
}
