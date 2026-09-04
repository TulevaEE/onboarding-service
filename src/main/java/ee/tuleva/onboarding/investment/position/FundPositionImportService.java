package ee.tuleva.onboarding.investment.position;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundPositionImportService {

  private final FundPositionRepository repository;
  private final Clock clock;

  @Transactional
  public int importNewPositions(List<FundPosition> positions) {
    int imported = 0;

    for (FundPosition position : positions) {
      boolean exists =
          repository.existsByNavDateAndFundAndAccountTypeAndAccountName(
              position.getNavDate(),
              position.getFund(),
              position.getAccountType(),
              position.getAccountName());

      if (!exists) {
        repository.save(position);
        imported++;
      }
    }

    log.info(
        "Import completed: total={}, imported={}, skipped={}",
        positions.size(),
        imported,
        positions.size() - imported);

    return imported;
  }

  @Transactional
  public ImportResult upsertPositions(List<FundPosition> positions) {
    List<FundPosition> importedPositions = new ArrayList<>();
    List<FundPosition> updatedPositions = new ArrayList<>();

    for (FundPosition position : positions) {
      Optional<FundPosition> existing =
          repository.findByNavDateAndFundAndAccountTypeAndAccountName(
              position.getNavDate(),
              position.getFund(),
              position.getAccountType(),
              position.getAccountName());

      if (existing.isPresent()) {
        if (updateIfChanged(existing.get(), position)) {
          updatedPositions.add(existing.get());
        }
      } else {
        repository.save(position);
        importedPositions.add(position);
      }
    }

    ImportResult result =
        new ImportResult(List.copyOf(importedPositions), List.copyOf(updatedPositions));
    log.info(
        "Upsert completed: total={}, imported={}, updated={}, unchanged={}",
        positions.size(),
        result.imported(),
        result.updated(),
        positions.size() - result.changed());

    return result;
  }

  private boolean updateIfChanged(FundPosition existing, FundPosition incoming) {
    if (bigDecimalEquals(existing.getQuantity(), incoming.getQuantity())
        && bigDecimalEquals(existing.getMarketPrice(), incoming.getMarketPrice())
        && bigDecimalEquals(existing.getMarketValue(), incoming.getMarketValue())) {
      return false;
    }

    existing.setQuantity(incoming.getQuantity());
    existing.setMarketPrice(incoming.getMarketPrice());
    existing.setMarketValue(incoming.getMarketValue());
    existing.setUpdatedAt(clock.instant());
    repository.save(existing);
    return true;
  }

  private boolean bigDecimalEquals(@Nullable BigDecimal a, @Nullable BigDecimal b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.compareTo(b) == 0;
  }

  public record ImportResult(
      List<FundPosition> importedPositions, List<FundPosition> updatedPositions) {

    public static ImportResult none() {
      return new ImportResult(List.of(), List.of());
    }

    public int imported() {
      return importedPositions.size();
    }

    public int updated() {
      return updatedPositions.size();
    }

    public int changed() {
      return imported() + updated();
    }

    public Map<TulevaFund, Integer> changedRowsByFund() {
      return Stream.concat(importedPositions.stream(), updatedPositions.stream())
          .collect(
              groupingBy(FundPosition::getFund, collectingAndThen(counting(), Long::intValue)));
    }
  }
}
