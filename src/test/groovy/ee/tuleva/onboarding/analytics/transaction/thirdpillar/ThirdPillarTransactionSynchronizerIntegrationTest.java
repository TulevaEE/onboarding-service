package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder.EpisServiceHolder;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ThirdPillarTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@Transactional
@EnableEpisServiceHolder
class ThirdPillarTransactionSynchronizerIntegrationTest {

  private static final LocalDate SYNC_START_DATE = LocalDate.of(2023, 1, 10);
  private static final LocalDate SYNC_END_DATE = LocalDate.of(2023, 1, 20);
  private static final LocalDate DATE_WITHIN_RANGE = LocalDate.of(2023, 1, 15);
  private static final LocalDate DATE_OUTSIDE_RANGE = LocalDate.of(2023, 1, 5);

  private static final String DEFAULT_ACCOUNT_NO = "ACC_DEFAULT";
  private static final String DEFAULT_TRANSACTION_TYPE = "TYPE_DEFAULT";
  private static final BigDecimal DEFAULT_SHARE_AMOUNT = BigDecimal.ONE;
  private static final BigDecimal DEFAULT_SHARE_PRICE = BigDecimal.ONE;
  private static final BigDecimal DEFAULT_NAV = BigDecimal.ONE;
  private static final BigDecimal DEFAULT_TRANSACTION_VALUE = BigDecimal.ONE;

  @Autowired private ThirdPillarTransactionSynchronizer synchronizer;
  @Autowired private AnalyticsThirdPillarTransactionRepository repository;
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
  void sync_deletesExistingForDateRangeAndInsertsNew() {
    AnalyticsThirdPillarTransaction existingInRange =
        AnalyticsThirdPillarTransaction.builder()
            .reportingDate(DATE_WITHIN_RANGE)
            .personalId("ID_OLD_IN")
            .fullName("Old In Range")
            .accountNo(DEFAULT_ACCOUNT_NO)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .shareAmount(DEFAULT_SHARE_AMOUNT)
            .sharePrice(DEFAULT_SHARE_PRICE)
            .nav(DEFAULT_NAV)
            .transactionValue(DEFAULT_TRANSACTION_VALUE)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    AnalyticsThirdPillarTransaction existingOutsideRange =
        AnalyticsThirdPillarTransaction.builder()
            .reportingDate(DATE_OUTSIDE_RANGE)
            .personalId("ID_OLD_OUT")
            .fullName("Old Out Range")
            .accountNo(DEFAULT_ACCOUNT_NO)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .shareAmount(DEFAULT_SHARE_AMOUNT)
            .sharePrice(DEFAULT_SHARE_PRICE)
            .nav(DEFAULT_NAV)
            .transactionValue(DEFAULT_TRANSACTION_VALUE)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.saveAll(List.of(existingInRange, existingOutsideRange));
    assertThat(repository.count()).isEqualTo(2);

    List<ThirdPillarTransactionDto> incomingTxs =
        List.of(
            ThirdPillarTransactionFixture.johnDoeTransaction(),
            ThirdPillarTransactionFixture.janeSmithTransaction());
    mockEpisService.setPensionTransactions(incomingTxs);

    // when
    synchronizer.sync(SYNC_START_DATE, SYNC_END_DATE);

    // then
    List<AnalyticsThirdPillarTransaction> all = repository.findAll();
    assertThat(all).hasSize(3);

    assertThat(all.stream().noneMatch(t -> t.getPersonalId().equals("ID_OLD_IN"))).isTrue();
    assertThat(all.stream().anyMatch(t -> t.getPersonalId().equals("ID_OLD_OUT"))).isTrue();
    assertThat(all)
        .anyMatch(
            t -> t.getPersonalId().equals(ThirdPillarTransactionFixture.JOHN_DOE_PERSONAL_ID));
    assertThat(all)
        .anyMatch(
            t -> t.getPersonalId().equals(ThirdPillarTransactionFixture.JANE_SMITH_PERSONAL_ID));
    assertThat(all)
        .filteredOn(t -> !t.getPersonalId().equals("ID_OLD_OUT"))
        .allSatisfy(
            tx -> {
              assertThat(tx.getReportingDate()).isEqualTo(DATE_WITHIN_RANGE);
              assertThat(tx.getDateCreated()).isEqualTo(LocalDateTime.now(TestClockHolder.clock));
            });
  }

  @Test
  void sync_handlesEmptyResponseGracefully() {
    // given
    AnalyticsThirdPillarTransaction existing =
        AnalyticsThirdPillarTransaction.builder()
            .reportingDate(DATE_WITHIN_RANGE)
            .personalId("ID_EXISTING")
            .fullName("Existing")
            .accountNo(DEFAULT_ACCOUNT_NO)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .shareAmount(DEFAULT_SHARE_AMOUNT)
            .sharePrice(DEFAULT_SHARE_PRICE)
            .nav(DEFAULT_NAV)
            .transactionValue(DEFAULT_TRANSACTION_VALUE)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.save(existing);
    assertThat(repository.count()).isEqualTo(1);

    mockEpisService.setPensionTransactions(Collections.emptyList());

    // when
    synchronizer.sync(SYNC_START_DATE, SYNC_END_DATE);

    // then
    List<AnalyticsThirdPillarTransaction> all = repository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getPersonalId()).isEqualTo("ID_EXISTING");
  }

  static class MockEpisService extends EpisService {
    private List<ThirdPillarTransactionDto> thirdPillarTransactionDtos = new ArrayList<>();

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    @Override
    public List<ThirdPillarTransactionDto> getTransactions(LocalDate startDate, LocalDate endDate) {
      return this.thirdPillarTransactionDtos;
    }

    public void setPensionTransactions(List<ThirdPillarTransactionDto> transactions) {
      this.thirdPillarTransactionDtos = new ArrayList<>(transactions);
    }
  }
}
