package ee.tuleva.onboarding.config;

import static java.util.Comparator.naturalOrder;

import eu.europa.esig.dss.model.tsl.LOTLInfo;
import eu.europa.esig.dss.model.tsl.ParsingInfoRecord;
import eu.europa.esig.dss.model.tsl.TLInfo;
import eu.europa.esig.dss.model.tsl.TLValidationJobSummary;
import eu.europa.esig.dss.model.tsl.ValidationInfoRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.Configuration;
import org.digidoc4j.TSLRefreshCallback;
import org.digidoc4j.exceptions.TslRefreshException;
import org.digidoc4j.impl.asic.tsl.DefaultTSLRefreshCallback;
import org.jspecify.annotations.Nullable;

@Slf4j
class LastValidTrustListRefreshCallback implements TSLRefreshCallback {

  private final TSLRefreshCallback defaultCallback;
  private final Clock clock;

  LastValidTrustListRefreshCallback(Configuration configuration, Clock clock) {
    this.defaultCallback = new DefaultTSLRefreshCallback(configuration);
    this.clock = clock;
  }

  @Override
  public boolean ensureTSLState(TLValidationJobSummary summary) {
    try {
      return defaultCallback.ensureTSLState(summary);
    } catch (TslRefreshException e) {
      if (!lastLoadedListsStillValid(summary)) {
        throw e;
      }
      log.error(
          "Trust list refresh failed, keeping the last validly signed lists until their next"
              + " update: nextUpdate={}, error={}",
          earliestNextUpdate(summary),
          e.getMessage());
      return true;
    }
  }

  private static Instant earliestNextUpdate(TLValidationJobSummary summary) {
    return summary.getLOTLInfos().stream()
        .flatMap(
            listOfTrustedLists ->
                Stream.concat(
                    Stream.of(listOfTrustedLists), listOfTrustedLists.getTLInfos().stream()))
        .map(list -> list.getParsingCacheInfo().getNextUpdateDate().toInstant())
        .min(naturalOrder())
        .orElseThrow();
  }

  private boolean lastLoadedListsStillValid(TLValidationJobSummary summary) {
    List<LOTLInfo> listsOfTrustedLists = summary.getLOTLInfos();
    return !listsOfTrustedLists.isEmpty()
        && listsOfTrustedLists.stream().allMatch(this::stillValidWithItsTrustedLists);
  }

  private boolean stillValidWithItsTrustedLists(LOTLInfo listOfTrustedLists) {
    List<TLInfo> trustedLists = listOfTrustedLists.getTLInfos();
    return stillValid(listOfTrustedLists)
        && !trustedLists.isEmpty()
        && trustedLists.stream().allMatch(this::stillValid);
  }

  private boolean stillValid(TLInfo list) {
    @Nullable ParsingInfoRecord parsing = list.getParsingCacheInfo();
    @Nullable ValidationInfoRecord validation = list.getValidationCacheInfo();
    return parsing != null
        && parsing.isSynchronized()
        && beforeNextUpdate(parsing.getNextUpdateDate())
        && validation != null
        && validation.isSynchronized()
        && validation.isValid();
  }

  private boolean beforeNextUpdate(@Nullable Date nextUpdate) {
    return nextUpdate != null && clock.instant().isBefore(nextUpdate.toInstant());
  }
}
