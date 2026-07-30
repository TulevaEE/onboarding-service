package ee.tuleva.onboarding.party;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record ChildAmlBackfillResult(
    boolean dryRun, int total, Map<Outcome, Long> counts, List<ChildResult> children) {

  public static ChildAmlBackfillResult of(boolean dryRun, List<ChildResult> children) {
    return new ChildAmlBackfillResult(
        dryRun,
        children.size(),
        children.stream().collect(groupingBy(ChildResult::outcome, TreeMap::new, counting())),
        children);
  }

  public record ChildResult(
      String childPersonalCode,
      Outcome outcome,
      CustodyVerification.@Nullable Outcome custodyOutcome,
      @Nullable String citizenship,
      ScreeningStatus screeningStatus,
      boolean hasUser,
      @Nullable String error) {

    static ChildResult skipped(String childPersonalCode, Outcome outcome, boolean hasUser) {
      return new ChildResult(
          childPersonalCode, outcome, null, null, ScreeningStatus.NOT_ATTEMPTED, hasUser, null);
    }

    static ChildResult error(String childPersonalCode, boolean hasUser, RuntimeException e) {
      return new ChildResult(
          childPersonalCode,
          Outcome.ERROR,
          null,
          null,
          ScreeningStatus.NOT_ATTEMPTED,
          hasUser,
          e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  public enum Outcome {
    BACKFILLED,
    CUSTODY_NOT_VERIFIED,
    GUARDIAN_LINK,
    ALREADY_BACKFILLED,
    TURNED_ADULT,
    WOULD_PROCESS,
    ERROR
  }

  public enum ScreeningStatus {
    SCREENED,
    SCREENING_FAILED,
    SKIPPED,
    NOT_ATTEMPTED
  }
}
