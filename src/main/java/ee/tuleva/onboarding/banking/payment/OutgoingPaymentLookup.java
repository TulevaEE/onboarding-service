package ee.tuleva.onboarding.banking.payment;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutgoingPaymentLookup {

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final EndToEndIdConverter endToEndIdConverter;

  public Optional<OutgoingPaymentStatus> findStatusForSource(UUID sourceId) {
    return outgoingPaymentRepository
        .findByEndToEndId(endToEndIdConverter.toEndToEndId(sourceId))
        .map(OutgoingPayment::getStatus);
  }

  public boolean isOurs(@Nullable String endToEndId) {
    return endToEndId != null && outgoingPaymentRepository.findByEndToEndId(endToEndId).isPresent();
  }
}
