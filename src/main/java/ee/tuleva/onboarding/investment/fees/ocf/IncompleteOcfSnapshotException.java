package ee.tuleva.onboarding.investment.fees.ocf;

public class IncompleteOcfSnapshotException extends IllegalStateException {

  IncompleteOcfSnapshotException(OcfSnapshot snapshot) {
    super(
        "Refusing to publish an incomplete OCF snapshot: fund="
            + snapshot.fundCode()
            + ", month="
            + snapshot.snapshotMonth()
            + ", checks="
            + snapshot.checks());
  }
}
