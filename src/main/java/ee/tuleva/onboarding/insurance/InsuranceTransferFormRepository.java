package ee.tuleva.onboarding.insurance;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface InsuranceTransferFormRepository extends CrudRepository<InsuranceTransferForm, Long> {

  Optional<InsuranceTransferForm> findByIdAndUserId(Long id, Long userId);

  @Lock(PESSIMISTIC_WRITE)
  @Query("select form from InsuranceTransferForm form where form.id = :id")
  Optional<InsuranceTransferForm> findForUpdateById(@Param("id") Long id);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      update InsuranceTransferForm form
      set form.filledPdf = null, form.signedContainer = null
      where form.signedAt < :signedBefore
        and (form.filledPdf is not null or form.signedContainer is not null)
      """)
  int clearDocumentsOfFormsSignedBefore(@Param("signedBefore") Instant signedBefore);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      update InsuranceTransferForm form
      set form.filledPdf = null
      where form.signedAt is null
        and form.createdAt < :createdBefore
        and form.filledPdf is not null
      """)
  int clearDocumentsOfUnsignedFormsCreatedBefore(@Param("createdBefore") Instant createdBefore);
}
