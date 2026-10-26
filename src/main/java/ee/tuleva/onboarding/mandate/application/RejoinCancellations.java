package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN_CANCELLATION;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RejoinCancellations {

  private final MandateRepository mandateRepository;
  private final MandateProcessorService mandateProcessor;

  boolean isCanceled(AuthenticatedPerson person, ApplicationSnapshot rejoin) {
    return mandateRepository
        .findAllByUserIdAndCreatedDateAfter(person.getUserIdOrThrow(), rejoin.getDate())
        .stream()
        .filter(mandate -> Set.of(REJOIN, REJOIN_CANCELLATION).contains(mandate.getMandateType()))
        .filter(mandateProcessor::hasSucceeded)
        .max(comparing(Mandate::getCreatedDate))
        .map(Mandate::isRejoinCancellation)
        .orElse(false);
  }
}
