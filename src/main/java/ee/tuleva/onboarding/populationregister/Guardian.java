package ee.tuleva.onboarding.populationregister;

// Direction-neutral custody counterpart, mapped from a CHILD-subject custody query's hooldusoigused
// where teineIsik is the guardian. Deliberately NOT CustodyRight: that type's childPersonalCode /
// childAlive fields are parent-subject-oriented and would mean "other person" / "other-person-alive"
// on a child-subject query. Here personalCode/alive describe the guardian.
public record Guardian(
    String personalCode, boolean assetManagement, boolean valid, boolean alive) {

  // Property (H20) custody, currently valid, held by a living guardian.
  public boolean grantsAssetManagement() {
    return assetManagement && valid && alive;
  }
}
