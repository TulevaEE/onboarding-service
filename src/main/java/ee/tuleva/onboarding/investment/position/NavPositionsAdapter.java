package ee.tuleva.onboarding.investment.position;

import ee.tuleva.onboarding.savings.fund.nav.NavPosition;
import ee.tuleva.onboarding.savings.fund.nav.NavPositions;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NavPositionsAdapter implements NavPositions {

  private final FundPositionRepository fundPositionRepository;

  @Override
  public Optional<LocalDate> findLatestNavDateByFundAndAsOfDate(
      TulevaFund fund, LocalDate asOfDate) {
    return fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
  }

  @Override
  public List<NavPosition> findLiabilityPositions(LocalDate navDate, TulevaFund fund) {
    return toNavPositions(
        fundPositionRepository.findByNavDateAndFundAndAccountType(
            navDate, fund, AccountType.LIABILITY));
  }

  @Override
  public List<NavPosition> findReceivablePositions(LocalDate navDate, TulevaFund fund) {
    return toNavPositions(
        fundPositionRepository.findByNavDateAndFundAndAccountType(
            navDate, fund, AccountType.RECEIVABLES));
  }

  private List<NavPosition> toNavPositions(List<FundPosition> positions) {
    return positions.stream()
        .map(position -> new NavPosition(position.getAccountName(), position.getMarketValue()))
        .toList();
  }
}
