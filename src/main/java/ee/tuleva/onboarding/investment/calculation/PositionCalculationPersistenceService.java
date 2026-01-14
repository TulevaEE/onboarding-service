package ee.tuleva.onboarding.investment.calculation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionCalculationPersistenceService {

  private final PositionCalculationRepository repository;

  @Transactional
  public void saveAll(List<PositionCalculation> calculations) {
    for (PositionCalculation calculation : calculations) {
      save(calculation);
    }
  }

  @Transactional
  public void save(PositionCalculation calculation) {
    InvestmentPositionCalculation entity = toEntity(calculation);

    repository
        .findByIsinAndFundAndDate(calculation.isin(), calculation.fund(), calculation.date())
        .ifPresentOrElse(
            existing -> {
              updateEntity(existing, calculation);
              repository.save(existing);
              log.debug(
                  "Updated position calculation: isin={}, fund={}, date={}",
                  calculation.isin(),
                  calculation.fund(),
                  calculation.date());
            },
            () -> {
              repository.save(entity);
              log.debug(
                  "Saved new position calculation: isin={}, fund={}, date={}",
                  calculation.isin(),
                  calculation.fund(),
                  calculation.date());
            });
  }

  private InvestmentPositionCalculation toEntity(PositionCalculation calculation) {
    return InvestmentPositionCalculation.builder()
        .isin(calculation.isin())
        .fund(calculation.fund())
        .date(calculation.date())
        .quantity(calculation.quantity())
        .eodhdPrice(calculation.eodhdPrice())
        .yahooPrice(calculation.yahooPrice())
        .usedPrice(calculation.usedPrice())
        .priceSource(calculation.priceSource())
        .calculatedMarketValue(calculation.calculatedMarketValue())
        .validationStatus(calculation.validationStatus())
        .priceDiscrepancyPercent(calculation.priceDiscrepancyPercent())
        .createdAt(calculation.createdAt())
        .build();
  }

  private void updateEntity(InvestmentPositionCalculation entity, PositionCalculation calculation) {
    entity.setQuantity(calculation.quantity());
    entity.setEodhdPrice(calculation.eodhdPrice());
    entity.setYahooPrice(calculation.yahooPrice());
    entity.setUsedPrice(calculation.usedPrice());
    entity.setPriceSource(calculation.priceSource());
    entity.setCalculatedMarketValue(calculation.calculatedMarketValue());
    entity.setValidationStatus(calculation.validationStatus());
    entity.setPriceDiscrepancyPercent(calculation.priceDiscrepancyPercent());
    entity.setCreatedAt(calculation.createdAt());
  }
}
