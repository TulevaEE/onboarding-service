package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Optional;

public interface OngoingChargesFigures {

  Optional<PublishedFigure> figureInForceOn(TulevaFund fund, LocalDate date);
}
