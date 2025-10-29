package ee.tuleva.onboarding.analytics.transaction.unitowner;

import static ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.time.TestClockHolder;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UnitOwnerRepositoryTest {

  @Autowired UnitOwnerRepository repository;
  @Autowired DataSource dataSource;
  private final LocalDateTime creationTime = LocalDateTime.now(TestClockHolder.clock);

  private static final String CREATE_UNIT_OWNER_TABLE =
      """
      CREATE TABLE IF NOT EXISTS unit_owner (
          id BIGSERIAL PRIMARY KEY,
          personal_id VARCHAR(255) NOT NULL,
          snapshot_date DATE NOT NULL,
          first_name VARCHAR(255),
          last_name VARCHAR(255),
          phone VARCHAR(255),
          email VARCHAR(255),
          country VARCHAR(255),
          language_preference VARCHAR(255),
          pension_account VARCHAR(255),
          death_date DATE,
          fund_manager VARCHAR(255),
          p2_choice VARCHAR(255), p2_choice_method VARCHAR(255), p2_choice_date DATE,
          p2_rava_date DATE, p2_rava_status VARCHAR(255), p2_mmte_date DATE, p2_mmte_status VARCHAR(255),
          p2_rate INTEGER, p2_next_rate INTEGER, p2_next_rate_date DATE,
          p2_ykva_date DATE, p2_plav_date DATE, p2_fpaa_date DATE, p2_duty_start DATE, p2_duty_end DATE,
          p3_identification_date DATE, p3_identifier VARCHAR(255), p3_block_flag VARCHAR(255), p3_blocker VARCHAR(255),
          date_created TIMESTAMP NOT NULL,
          CONSTRAINT uq_unit_owner_personal_id_snapshot_date_test UNIQUE (personal_id, snapshot_date)
      );
      """;
  private static final String CREATE_UNIT_OWNER_BALANCE_TABLE =
      """
     CREATE TABLE IF NOT EXISTS unit_owner_balance (
         unit_owner_id BIGINT NOT NULL,
         security_short_name VARCHAR(255),
         security_name VARCHAR(255),
         balance_type VARCHAR(255),
         balance_amount NUMERIC(19, 8),
         start_date DATE,
         last_updated DATE,
         CONSTRAINT fk_unit_owner_balance_owner_test
             FOREIGN KEY(unit_owner_id)
             REFERENCES unit_owner(id)
             ON DELETE CASCADE
     );
     """;

  private static final String DELETE_BALANCES = "DELETE FROM unit_owner_balance;";
  private static final String DELETE_OWNERS = "DELETE FROM unit_owner;";

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS unit_owner_balance CASCADE");
      stmt.execute("DROP TABLE IF EXISTS unit_owner CASCADE");
      stmt.execute(CREATE_UNIT_OWNER_TABLE);
      stmt.execute(CREATE_UNIT_OWNER_BALANCE_TABLE);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(DELETE_BALANCES);
      stmt.execute(DELETE_OWNERS);
    }
  }

  @Test
  @DisplayName("Can save and find UnitOwner by personal ID and snapshot date")
  void saveAndFindByPersonalIdAndSnapshotDate() {
    // given
    UnitOwner owner = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).build();
    repository.save(owner);

    // when
    Optional<UnitOwner> found =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_1, SNAPSHOT_DATE_1);
    Optional<UnitOwner> notFound =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_1, SNAPSHOT_DATE_2);

    // then
    assertThat(found).isPresent();
    UnitOwner foundOwner = found.get();
    assertThat(foundOwner.getPersonalId()).isEqualTo(PERSON_ID_1);
    assertThat(foundOwner.getSnapshotDate()).isEqualTo(SNAPSHOT_DATE_1);
    assertThat(foundOwner.getLastName()).isEqualTo("Maasikas");
    assertThat(foundOwner.getBalances()).hasSize(2);
    assertThat(notFound).isEmpty();
  }

  @Test
  @DisplayName("Can save multiple snapshots for the same personal ID")
  void save_multipleSnapshotsForSamePerson() {
    // given
    UnitOwner owner1 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner2 =
        entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_2, creationTime).firstName("Mari Ann").build();

    // when
    repository.saveAll(List.of(owner1, owner2));

    // then
    assertThat(repository.count()).isEqualTo(2);
    Optional<UnitOwner> found1 =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_1, SNAPSHOT_DATE_1);
    Optional<UnitOwner> found2 =
        repository.findByPersonalIdAndSnapshotDate(PERSON_ID_1, SNAPSHOT_DATE_2);
    assertThat(found1).isPresent();
    assertThat(found2).isPresent();
    assertThat(found1.get().getFirstName()).isEqualTo("Mari");
    assertThat(found2.get().getFirstName()).isEqualTo("Mari Ann");
  }

  @Test
  @DisplayName(
      "Saving owner with same personal ID and snapshot date throws DataIntegrityViolationException")
  void save_duplicatePersonalIdAndDateThrowsException() {
    // given
    UnitOwner owner1 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner2 =
        entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).email("new@email.com").build();

    repository.saveAndFlush(owner1);

    // when & then
    assertThatThrownBy(() -> repository.saveAndFlush(owner2))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_unit_owner_personal_id_snapshot_date");
  }

  @Test
  @DisplayName("findBySnapshotDate returns all owners for that date")
  void findBySnapshotDate_returnsCorrectOwners() {
    // given
    UnitOwner owner1Date1 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner2Date1 = entityBuilder(PERSON_ID_2, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner1Date2 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_2, creationTime).build();
    repository.saveAll(List.of(owner1Date1, owner2Date1, owner1Date2));

    // when
    List<UnitOwner> foundDate1 = repository.findBySnapshotDate(SNAPSHOT_DATE_1);
    List<UnitOwner> foundDate2 = repository.findBySnapshotDate(SNAPSHOT_DATE_2);

    // then
    assertThat(foundDate1).hasSize(2);
    assertThat(foundDate1)
        .extracting(UnitOwner::getPersonalId)
        .containsExactlyInAnyOrder(PERSON_ID_1, PERSON_ID_2);
    assertThat(foundDate2).hasSize(1);
    assertThat(foundDate2.get(0).getPersonalId()).isEqualTo(PERSON_ID_1);
  }

  @Test
  @DisplayName("findLatestSnapshotDate returns max date")
  void findLatestSnapshotDate_returnsMaxDate() {
    // given
    UnitOwner owner1Date1 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner2Date1 = entityBuilder(PERSON_ID_2, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner1Date2 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_2, creationTime).build();
    repository.saveAll(List.of(owner1Date1, owner2Date1, owner1Date2));

    // when
    Optional<LocalDate> latestDate = repository.findLatestSnapshotDate();

    // then
    assertThat(latestDate).isPresent().hasValue(SNAPSHOT_DATE_2);
  }

  @Test
  @DisplayName("findDistinctPersonalIdsBySnapshotDate returns correct IDs")
  void findDistinctPersonalIdsBySnapshotDate_returnsCorrectIds() {
    // given
    UnitOwner owner1Date1 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner2Date1 = entityBuilder(PERSON_ID_2, SNAPSHOT_DATE_1, creationTime).build();
    UnitOwner owner1Date2 = entityBuilder(PERSON_ID_1, SNAPSHOT_DATE_2, creationTime).build();
    repository.saveAll(List.of(owner1Date1, owner2Date1, owner1Date2));

    // when
    List<String> idsDate1 = repository.findDistinctPersonalIdsBySnapshotDate(SNAPSHOT_DATE_1);
    List<String> idsDate2 = repository.findDistinctPersonalIdsBySnapshotDate(SNAPSHOT_DATE_2);

    // then
    assertThat(idsDate1).containsExactlyInAnyOrder(PERSON_ID_1, PERSON_ID_2);
    assertThat(idsDate2).containsExactly(PERSON_ID_1);
  }
}
