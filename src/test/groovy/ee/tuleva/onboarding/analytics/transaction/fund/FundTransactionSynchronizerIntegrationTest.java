package ee.tuleva.onboarding.analytics.transaction.fund;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder.EpisServiceHolder;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
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
class FundTransactionSynchronizerIntegrationTest {

  private static final String SYNC_ISIN = "EE_SYNC_ISIN";
  private static final LocalDate SYNC_START_DATE = LocalDate.of(2025, 4, 1);
  private static final LocalDate SYNC_END_DATE = LocalDate.of(2025, 4, 10);
  private static final LocalDate DATE_WITHIN_RANGE = LocalDate.of(2025, 4, 5);
  private static final LocalDate DATE_OUTSIDE_RANGE = LocalDate.of(2025, 3, 31);

  private static final String DEFAULT_TRANSACTION_TYPE = "TEST_TYPE";
  private static final BigDecimal DEFAULT_PRICE = BigDecimal.valueOf(1.1);
  private static final BigDecimal DEFAULT_NAV = BigDecimal.valueOf(1.105);
  private static final BigDecimal DEFAULT_UNIT_AMOUNT = BigDecimal.valueOf(100);
  private static final String DEFAULT_PERSON_NAME = "Test Person";
  private static final String DEFAULT_PENSION_ACCOUNT = "TestAccount";
  private static final String DEFAULT_COUNTRY = "EE";

  @Autowired private FundTransactionSynchronizer synchronizer;
  @Autowired private FundTransactionRepository repository;
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
  void sync_deletesExistingForIsinAndDateRangeAndInsertsNew() {
    // given
    FundTransaction existingInRange =
        FundTransaction.builder()
            .isin(SYNC_ISIN)
            .transactionDate(DATE_WITHIN_RANGE)
            .personalId("ID_OLD_IN")
            .amount(BigDecimal.TEN)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .price(DEFAULT_PRICE)
            .nav(DEFAULT_NAV)
            .unitAmount(DEFAULT_UNIT_AMOUNT)
            .personName(DEFAULT_PERSON_NAME)
            .pensionAccount(DEFAULT_PENSION_ACCOUNT)
            .country(DEFAULT_COUNTRY)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    FundTransaction existingOutsideRange =
        FundTransaction.builder()
            .isin(SYNC_ISIN)
            .transactionDate(DATE_OUTSIDE_RANGE)
            .personalId("ID_OLD_OUT")
            .amount(BigDecimal.ONE)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .price(DEFAULT_PRICE)
            .nav(DEFAULT_NAV)
            .unitAmount(DEFAULT_UNIT_AMOUNT)
            .personName(DEFAULT_PERSON_NAME)
            .pensionAccount(DEFAULT_PENSION_ACCOUNT)
            .country(DEFAULT_COUNTRY)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    FundTransaction existingDifferentIsin =
        FundTransaction.builder()
            .isin("DIFFERENT_ISIN")
            .transactionDate(DATE_WITHIN_RANGE)
            .personalId("ID_OLD_DIFF")
            .amount(BigDecimal.ONE)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .price(DEFAULT_PRICE)
            .nav(DEFAULT_NAV)
            .unitAmount(DEFAULT_UNIT_AMOUNT)
            .personName(DEFAULT_PERSON_NAME)
            .pensionAccount(DEFAULT_PENSION_ACCOUNT)
            .country(DEFAULT_COUNTRY)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.saveAll(List.of(existingInRange, existingOutsideRange, existingDifferentIsin));
    assertThat(repository.count()).isEqualTo(3);

    List<FundTransactionDto> incomingTxs =
        List.of(
            FundTransactionFixture.Dto.newTransactionDto(),
            FundTransactionFixture.Dto.duplicateTransactionDto());
    assertThat(incomingTxs).allSatisfy(dto -> assertThat(dto.getTransactionType()).isNotNull());
    mockEpisService.setFundTransactions(incomingTxs);

    // when
    synchronizer.sync(SYNC_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // then
    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize(4);

    assertThat(all.stream().noneMatch(t -> t.getPersonalId().equals("ID_OLD_IN"))).isTrue();
    assertThat(all.stream().anyMatch(t -> t.getPersonalId().equals("ID_OLD_OUT"))).isTrue();
    assertThat(all.stream().anyMatch(t -> t.getPersonalId().equals("ID_OLD_DIFF"))).isTrue();
    assertThat(all)
        .anyMatch(
            t ->
                t.getPersonalId().equals(FundTransactionFixture.Dto.NEW_PERSONAL_ID)
                    && t.getIsin().equals(SYNC_ISIN));
    assertThat(all)
        .anyMatch(
            t ->
                t.getPersonalId().equals(FundTransactionFixture.Dto.DUPLICATE_PERSONAL_ID)
                    && t.getIsin().equals(SYNC_ISIN));
    assertThat(all)
        .filteredOn(
            t ->
                t.getIsin().equals(SYNC_ISIN) && t.getTransactionDate().isAfter(DATE_OUTSIDE_RANGE))
        .allSatisfy(
            tx ->
                assertThat(tx.getDateCreated())
                    .isEqualTo(LocalDateTime.now(TestClockHolder.clock)));
  }

  @Test
  void sync_handlesEmptyResponseGracefully() {
    // given
    FundTransaction existing =
        FundTransaction.builder()
            .isin(SYNC_ISIN)
            .transactionDate(DATE_WITHIN_RANGE)
            .personalId("ID_EXISTING")
            .amount(BigDecimal.TEN)
            .transactionType(DEFAULT_TRANSACTION_TYPE)
            .price(DEFAULT_PRICE)
            .nav(DEFAULT_NAV)
            .unitAmount(DEFAULT_UNIT_AMOUNT)
            .personName(DEFAULT_PERSON_NAME)
            .pensionAccount(DEFAULT_PENSION_ACCOUNT)
            .country(DEFAULT_COUNTRY)
            .dateCreated(LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.save(existing);
    assertThat(repository.count()).isEqualTo(1);

    mockEpisService.setFundTransactions(Collections.emptyList());

    // when
    synchronizer.sync(SYNC_ISIN, SYNC_START_DATE, SYNC_END_DATE);

    // then
    List<FundTransaction> all = repository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getPersonalId()).isEqualTo("ID_EXISTING");
  }

  static class MockEpisService extends EpisService {
    private List<FundTransactionDto> fundTransactions = new ArrayList<>();

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    @Override
    public List<FundTransactionDto> getFundTransactions(
        String isin, LocalDate fromDate, LocalDate toDate) {
      return this.fundTransactions;
    }

    public void setFundTransactions(List<FundTransactionDto> transactions) {
      this.fundTransactions = new ArrayList<>(transactions);
    }
  }
}
