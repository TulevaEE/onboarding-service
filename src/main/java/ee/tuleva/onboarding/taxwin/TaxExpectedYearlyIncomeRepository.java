package ee.tuleva.onboarding.taxwin;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface TaxExpectedYearlyIncomeRepository extends CrudRepository<TaxExpectedYearlyIncome, Long> {

  Optional<TaxExpectedYearlyIncome> findByUserId(Long userId);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query("delete from TaxExpectedYearlyIncome yearly where yearly.userId = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from TaxExpectedYearlyIncome yearly
      where not exists (
        select consent from TaxDataConsent consent
        where consent.userId = yearly.userId
          and consent.status = ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED
          and consent.refreshedAt >= :refreshedSince)
      """)
  int deleteWithoutConsentRefreshedSince(@Param("refreshedSince") Instant refreshedSince);
}
