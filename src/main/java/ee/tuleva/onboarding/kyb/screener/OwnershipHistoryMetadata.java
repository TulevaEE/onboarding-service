package ee.tuleva.onboarding.kyb.screener;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.Comparator.nullsLast;

import ee.tuleva.onboarding.kyb.KybOwnership;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OwnershipHistoryMetadata {

  private static final String OWNERSHIPS = "ownerships";
  private static final String CODE = "code";
  private static final String ROLE = "role";
  private static final String START_DATE = "startDate";
  private static final String END_DATE = "endDate";

  private static final Comparator<KybOwnership> OWNERSHIP_ORDER =
      comparing(KybOwnership::code, nullsLast(naturalOrder()))
          .thenComparing(KybOwnership::role)
          .thenComparing(KybOwnership::startDate, nullsFirst(naturalOrder()))
          .thenComparing(KybOwnership::endDate, nullsLast(naturalOrder()));

  private OwnershipHistoryMetadata() {}

  static Map<String, Object> of(final List<KybOwnership> ownerships) {
    return Map.of(
        OWNERSHIPS,
        ownerships.stream()
            .sorted(OWNERSHIP_ORDER)
            .map(OwnershipHistoryMetadata::ownershipMetadata)
            .toList());
  }

  private static Map<String, Object> ownershipMetadata(KybOwnership ownership) {
    var metadata = new HashMap<String, Object>();
    var code = ownership.code();
    if (code != null) {
      metadata.put(CODE, code);
    }
    metadata.put(ROLE, ownership.role().name());
    var startDate = ownership.startDate();
    if (startDate != null) {
      metadata.put(START_DATE, startDate.toString());
    }
    var endDate = ownership.endDate();
    if (endDate != null) {
      metadata.put(END_DATE, endDate.toString());
    }
    return Map.copyOf(metadata);
  }
}
