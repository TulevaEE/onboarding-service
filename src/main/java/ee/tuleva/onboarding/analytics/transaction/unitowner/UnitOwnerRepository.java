package ee.tuleva.onboarding.analytics.transaction.unitowner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UnitOwnerRepository extends JpaRepository<UnitOwner, Long> {

  Optional<UnitOwner> findByPersonalIdAndSnapshotDate(String personalId, LocalDate snapshotDate);

  List<UnitOwner> findBySnapshotDate(LocalDate snapshotDate);

  boolean existsBySnapshotDate(LocalDate snapshotDate);

  @Query("SELECT DISTINCT uo.snapshotDate FROM UnitOwner uo")
  List<LocalDate> findDistinctSnapshotDates();

  @Modifying
  @Transactional
  @Query("DELETE FROM UnitOwner uo WHERE uo.snapshotDate IN :snapshotDates")
  int deleteBySnapshotDateIn(List<LocalDate> snapshotDates);

  @Query("SELECT MAX(uo.snapshotDate) FROM UnitOwner uo")
  Optional<LocalDate> findLatestSnapshotDate();

  @Query("SELECT DISTINCT uo.personalId FROM UnitOwner uo WHERE uo.snapshotDate = :snapshotDate")
  List<String> findDistinctPersonalIdsBySnapshotDate(LocalDate snapshotDate);
}
