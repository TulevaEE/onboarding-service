package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.fund.OngoingChargesFigures;
import ee.tuleva.onboarding.fund.PublishedFigure;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KidOngoingChargesFigures implements OngoingChargesFigures {

  private final OcfPublicationRepository publicationRepository;

  @Override
  public Optional<PublishedFigure> figureInForceOn(TulevaFund fund, LocalDate date) {
    return publicationRepository
        .findInForceOn(fund.getCode(), date)
        .map(KidOngoingChargesFigures::asTheKidPrintsIt);
  }

  private static PublishedFigure asTheKidPrintsIt(KidFigure kid) {
    return new PublishedFigure(
        kid.precision().printedFraction(kid.publishedOcf()), kid.publishedDecimals());
  }
}
