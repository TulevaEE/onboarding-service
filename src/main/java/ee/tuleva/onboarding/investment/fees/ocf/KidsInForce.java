package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KidsInForce {

  private final OcfPublicationRepository publicationRepository;

  public Optional<LocalDate> validFromOfTheOneInForceOn(TulevaFund fund, LocalDate date) {
    return publicationRepository.findInForceOn(fund.getCode(), date).map(KidFigure::validFrom);
  }
}
