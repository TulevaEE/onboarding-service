package ee.tuleva.onboarding.taxwin;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface TaxIncomeMonthRepository extends CrudRepository<TaxIncomeMonth, Long> {

  List<TaxIncomeMonth> findAllByUserId(Long userId);

  @Transactional
  @Modifying(flushAutomatically = true)
  @Query("delete from TaxIncomeMonth month where month.userId = :userId")
  void deleteAllByUserId(@Param("userId") Long userId);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from TaxIncomeMonth month
      where not exists (
        select consent from TaxDataConsent consent
        where consent.userId = month.userId
          and consent.status = ee.tuleva.onboarding.taxwin.TaxDataConsentStatus.APPROVED
          and consent.refreshedAt >= :refreshedSince)
      """)
  int deleteWithoutConsentRefreshedSince(@Param("refreshedSince") Instant refreshedSince);
}
