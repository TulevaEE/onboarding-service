package ee.tuleva.onboarding.analytics.transaction.unitowner;

import static ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder;
import ee.tuleva.onboarding.epis.EnableEpisServiceHolder.EpisServiceHolder;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.epis.transaction.ThirdPillarTransactionDto;
import ee.tuleva.onboarding.epis.transaction.TransactionFundBalanceDto;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerDto;
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
class UnitOwnerSynchronizerIntegrationTest {

  @Autowired private UnitOwnerSynchronizer synchronizer;
  @Autowired private UnitOwnerRepository repository;
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
  void sync_insertsNewSnapshotRecords() {
    // given
    LocalDateTime time = LocalDateTime.now(ClockHolder.clock());
    LocalDate date1 = LocalDate.of(2025, 4, 20);
    LocalDate date2 = LocalDate.of(2025, 4, 21);

    List<UnitOwnerDto> incoming1 =
        List.of(
            dtoBuilder(PERSON_ID_1).firstName("Mari").build(),
            dtoBuilder(PERSON_ID_2).firstName("Jaan").build());
    mockEpisService.setUnitOwners(incoming1);
    synchronizer.sync(date1);

    // when
    List<UnitOwnerDto> incoming2 = List.of(dtoBuilder(PERSON_ID_1).firstName("Mari-Ann").build());
    mockEpisService.setUnitOwners(incoming2);
    synchronizer.sync(date2);

    // then
    List<UnitOwner> all = repository.findAll();
    assertThat(all).hasSize(3);

    List<UnitOwner> foundDate1 = repository.findBySnapshotDate(date1);
    assertThat(foundDate1).hasSize(2);
    Optional<UnitOwner> owner1Date1 =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_1, date1);
    Optional<UnitOwner> owner2Date1 =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_2, date1);
    assertThat(owner1Date1).isPresent();
    assertThat(owner1Date1.get().getFirstName()).isEqualTo("Mari");
    assertThat(owner2Date1).isPresent();
    assertThat(owner2Date1.get().getFirstName()).isEqualTo("Jaan");

    List<UnitOwner> foundDate2 = repository.findBySnapshotDate(date2);
    assertThat(foundDate2).hasSize(1);
    Optional<UnitOwner> owner1Date2 =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_1, date2);
    assertThat(owner1Date2).isPresent();
    assertThat(owner1Date2.get().getFirstName()).isEqualTo("Mari-Ann");
    Optional<UnitOwner> owner2Date2 =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_2, date2);
    assertThat(owner2Date2).isEmpty();

    assertThat(owner1Date2.get().getDateCreated()).isEqualTo(time);
  }

  @Test
  void sync_handlesEmptyResponseGracefully() {
    // given
    LocalDate date1 = LocalDate.of(2025, 4, 20);
    UnitOwner existingOwner =
        entityBuilder(
                PERSON_ID_1,
                date1.minusDays(1),
                LocalDateTime.now(ClockHolder.clock()).minusDays(1))
            .build();
    repository.save(existingOwner);
    assertThat(repository.count()).isEqualTo(1);

    mockEpisService.setUnitOwners(Collections.emptyList());

    // when
    synchronizer.sync(date1);

    // then
    List<UnitOwner> all = repository.findAll();
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getPersonalId()).isEqualTo(PERSON_ID_1);
    assertThat(all.get(0).getSnapshotDate()).isEqualTo(date1.minusDays(1));

    List<UnitOwner> foundDate1 = repository.findBySnapshotDate(date1);
    assertThat(foundDate1).isEmpty();
  }

  static class MockEpisService extends EpisService {
    private List<UnitOwnerDto> unitOwners = new ArrayList<>();

    public MockEpisService(RestTemplate restTemplate, JwtTokenUtil jwtTokenUtil) {
      super(restTemplate, jwtTokenUtil);
    }

    @Override
    public List<UnitOwnerDto> getUnitOwners() {
      return new ArrayList<>(this.unitOwners);
    }

    @Override
    public List<TransactionFundBalanceDto> getFundBalances(
        LocalDate requestDate) { // Corrected DTO type
      return Collections.emptyList();
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

    public void setUnitOwners(List<UnitOwnerDto> owners) {
      this.unitOwners = new ArrayList<>(owners);
    }
  }
}
