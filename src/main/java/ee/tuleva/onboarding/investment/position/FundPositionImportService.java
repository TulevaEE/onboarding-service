package ee.tuleva.onboarding.investment.position;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    int imported = 0;
    int updated = 0;

    for (FundPosition position : positions) {
      Optional<FundPosition> existing =
          repository.findByNavDateAndFundAndAccountTypeAndAccountName(
              position.getNavDate(),
              position.getFund(),
              position.getAccountType(),
              position.getAccountName());

      if (existing.isPresent()) {
        if (updateIfChanged(existing.get(), position)) {
          updated++;
        }
      } else {
        repository.save(position);
        imported++;
      }
    }

    log.info(
        "Upsert completed: total={}, imported={}, updated={}, unchanged={}",
        positions.size(),
        imported,
        updated,
        positions.size() - imported - updated);

    return new ImportResult(imported, updated);
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

  private boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.compareTo(b) == 0;
  }

  public record ImportResult(int imported, int updated) {}
}
