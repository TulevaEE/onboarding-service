package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NOT_ASSET_MANAGEMENT;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;

import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustodyVerificationService {

  private final PopulationRegisterClient populationRegisterClient;

  public CustodyVerification verify(
      String parentPersonalCode, String childPersonalCode, Duration maxAge) {
    // The population register returns personal (H10) and property (H20) custody as
    // separate entries, so a parent with full custody has BOTH for the same child.
    // Asset management requires the property right specifically — look for it across
    // all of the child's entries rather than inspecting whichever one comes first.
    List<CustodyRight> childCustodies =
        populationRegisterClient.fetchCustodyRights(parentPersonalCode, maxAge).stream()
            .filter(right -> right.childPersonalCode().equals(childPersonalCode))
            .toList();

    if (childCustodies.isEmpty()) {
      log.info(
          "No custody record for child: parentCode={}, childCode={}",
          parentPersonalCode,
          childPersonalCode);
      return CustodyVerification.notVerified(NO_CUSTODY);
    }
    if (childCustodies.stream().noneMatch(CustodyRight::childAlive)) {
      return CustodyVerification.notVerified(CHILD_NOT_ALIVE);
    }

    Optional<CustodyRight> assetManagement =
        childCustodies.stream().filter(CustodyRight::grantsAssetManagement).findFirst();
    if (assetManagement.isEmpty()) {
      log.info(
          "Custody does not grant asset management: parentCode={}, childCode={}, types={}",
          parentPersonalCode,
          childPersonalCode,
          childCustodies.stream().map(CustodyRight::type).toList());
      return CustodyVerification.notVerified(NOT_ASSET_MANAGEMENT);
    }

    PopulationRegisterPerson child =
        populationRegisterClient.fetchPerson(childPersonalCode, maxAge);
    if (!child.isAlive()) {
      return CustodyVerification.notVerified(CHILD_NOT_ALIVE);
    }
    return new CustodyVerification(OK, child, evidence(assetManagement.get()));
  }

  private Map<String, Object> evidence(CustodyRight custody) {
    return Map.of(
        "custodyType", custody.type().name(),
        "valid", custody.valid(),
        "childAlive", custody.childAlive(),
        "childPersonalCode", custody.childPersonalCode());
  }
}
