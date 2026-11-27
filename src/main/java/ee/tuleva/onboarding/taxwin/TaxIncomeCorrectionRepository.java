package ee.tuleva.onboarding.taxwin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface TaxIncomeCorrectionRepository extends CrudRepository<TaxIncomeCorrection, Long> {

  List<TaxIncomeCorrection> findAllByUserId(Long userId);

  Optional<TaxIncomeCorrection> findByUserIdAndIncomeMonth(Long userId, LocalDate incomeMonth);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query("delete from TaxIncomeCorrection correction where correction.userId = :userId")
  void deleteAllByUserId(@Param("userId") Long userId);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      """
      delete from TaxIncomeCorrection correction
      where correction.userId = :userId and correction.incomeMonth in :months
      """)
  void deleteByUserIdAndIncomeMonthIn(
      @Param("userId") Long userId, @Param("months") Collection<LocalDate> months);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from TaxIncomeCorrection correction
      where not exists (
        select consent from TaxDataConsent consent
        where consent.userId = correction.userId
          and consent.status = ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED
          and consent.refreshedAt >= :refreshedSince)
      """)
  int deleteWithoutConsentRefreshedSince(@Param("refreshedSince") Instant refreshedSince);
}
