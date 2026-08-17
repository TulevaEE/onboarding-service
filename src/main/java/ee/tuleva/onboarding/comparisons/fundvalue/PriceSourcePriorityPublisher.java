package ee.tuleva.onboarding.comparisons.fundvalue;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the {@link PriorityPriceProvider#priceFeeds()} ordering into {@code
 * price_source_priority}, so SQL consumers rank price sources exactly the way this service does.
 *
 * <p>The Java list stays the single source of truth; the table is a derived copy, rewritten on
 * every application start. The consumer it exists for is the NAV price query in the tuleva repo
 * ({@code apps/nav-calc/instrumentide-värsked-hinnad.sql}), which carried its own hardcoded
 * ordering and had drifted out of step: on a same-date tie it preferred the exchange price where
 * {@link PriorityPriceProvider} prefers EODHD.
 *
 * <p>This does not affect price resolution — it only writes the table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceSourcePriorityPublisher {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  private static final String INSERT =
      """
      INSERT INTO price_source_priority (price_source, rank, updated_at)
      VALUES (:priceSource, :rank, now())
      """;

  /**
   * Replace wholesale rather than upsert: the table is derived, and a full rewrite inside one
   * transaction keeps the UNIQUE constraint on rank from tripping midway through a reordering.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void publish() {
    List<String> sources =
        PriorityPriceProvider.priceFeeds().stream().map(feed -> feed.source().name()).toList();

    jdbcTemplate.update("DELETE FROM price_source_priority", Map.of());
    for (int i = 0; i < sources.size(); i++) {
      jdbcTemplate.update(INSERT, Map.of("priceSource", sources.get(i), "rank", i + 1));
    }

    log.info("Published price source priority: {}", sources);
  }
}
