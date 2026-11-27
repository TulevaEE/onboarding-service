package ee.tuleva.onboarding.taxwin;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface TaxDataConsentRepository extends CrudRepository<TaxDataConsent, Long> {

  Optional<TaxDataConsent> findByUserId(Long userId);

  Optional<TaxDataConsent> findByIdAndUserId(Long id, Long userId);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update TaxDataConsent consent
      set consent.leaseUntil = :until
      where consent.id = :id and (consent.leaseUntil is null or consent.leaseUntil < :now)
      """)
  int claimLease(@Param("id") Long id, @Param("now") Instant now, @Param("until") Instant until);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update TaxDataConsent consent
      set consent.leaseUntil = null
      where consent.id = :id and consent.leaseUntil = :until
      """)
  void releaseLease(@Param("id") Long id, @Param("until") Instant until);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from TaxDataConsent consent
      where consent.status in :statuses and consent.createdAt < :createdBefore
      """)
  int deleteByStatusInAndCreatedAtBefore(
      @Param("statuses") Collection<TaxDataConsentStatus> statuses,
      @Param("createdBefore") Instant createdBefore);
}
