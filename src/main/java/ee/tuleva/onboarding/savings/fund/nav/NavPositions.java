package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NavPositions {

  Optional<LocalDate> findLatestNavDateByFundAndAsOfDate(TulevaFund fund, LocalDate asOfDate);

  List<NavPosition> findLiabilityPositions(LocalDate navDate, TulevaFund fund);

  List<NavPosition> findReceivablePositions(LocalDate navDate, TulevaFund fund);
}
