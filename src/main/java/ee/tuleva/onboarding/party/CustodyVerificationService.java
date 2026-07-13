package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NOT_ASSET_MANAGEMENT;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;

import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import ee.tuleva.onboarding.populationregister.PopulationRegisterResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustodyVerificationService {

  private final PopulationRegisterClient populationRegisterClient;

  public List<String> findChildrenWithAssetManagementCustody(
      String parentPersonalCode, Duration maxAge) {
    return populationRegisterClient.fetchCustodyRights(parentPersonalCode, maxAge).data().stream()
        .filter(CustodyRight::grantsAssetManagement)
        .map(CustodyRight::childPersonalCode)
        .distinct()
        .toList();
  }

  public CustodyVerification verify(
      String parentPersonalCode, String childPersonalCode, Duration maxAge) {
    PopulationRegisterResult<List<CustodyRight>> custodyResult =
        populationRegisterClient.fetchCustodyRights(parentPersonalCode, maxAge);
    UUID custodyMessageId = custodyResult.messageId();

    // The population register returns personal (H10) and property (H20) custody as
    // separate entries, so a parent with full custody has BOTH for the same child.
    // Asset management requires the property right specifically — look for it across
    // all of the child's entries rather than inspecting whichever one comes first.
    List<CustodyRight> childCustodies =
        custodyResult.data().stream()
            .filter(right -> right.childPersonalCode().equals(childPersonalCode))
            .toList();

    if (childCustodies.isEmpty()) {
      log.info(
          "No custody record for child: parentCode={}, childCode={}",
          parentPersonalCode,
          childPersonalCode);
      return CustodyVerification.notVerified(
          NO_CUSTODY, evidence(NO_CUSTODY, childPersonalCode, custodyMessageId));
    }
    if (childCustodies.stream().noneMatch(CustodyRight::childAlive)) {
      return CustodyVerification.notVerified(
          CHILD_NOT_ALIVE, evidence(CHILD_NOT_ALIVE, childPersonalCode, custodyMessageId));
    }

    Optional<CustodyRight> assetManagement =
        childCustodies.stream().filter(CustodyRight::grantsAssetManagement).findFirst();
    if (assetManagement.isEmpty()) {
      log.info(
          "Custody does not grant asset management: parentCode={}, childCode={}, types={}",
          parentPersonalCode,
          childPersonalCode,
          childCustodies.stream().map(CustodyRight::type).toList());
      return CustodyVerification.notVerified(
          NOT_ASSET_MANAGEMENT,
          evidence(NOT_ASSET_MANAGEMENT, childPersonalCode, custodyMessageId));
    }

    PopulationRegisterResult<PopulationRegisterPerson> childResult =
        populationRegisterClient.fetchPerson(parentPersonalCode, childPersonalCode, maxAge);
    PopulationRegisterPerson child = childResult.data();
    if (!child.isAlive()) {
      return CustodyVerification.notVerified(
          CHILD_NOT_ALIVE,
          evidence(CHILD_NOT_ALIVE, childPersonalCode, custodyMessageId, childResult.messageId()));
    }
    return new CustodyVerification(
        OK, child, evidence(assetManagement.get(), custodyMessageId, childResult.messageId()));
  }

  private Map<String, Object> evidence(
      CustodyRight custody, UUID custodyMessageId, UUID childMessageId) {
    Map<String, Object> evidence =
        new LinkedHashMap<>(
            evidence(OK, custody.childPersonalCode(), custodyMessageId, childMessageId));
    evidence.put("custodyType", custody.type().name());
    evidence.put("valid", custody.valid());
    evidence.put("childAlive", custody.childAlive());
    return Map.copyOf(evidence);
  }

  private Map<String, Object> evidence(
      CustodyVerification.Outcome outcome, String childPersonalCode, UUID custodyMessageId) {
    return Map.of(
        "outcome", outcome.name(),
        "childPersonalCode", childPersonalCode,
        "custodyResponseMessageId", custodyMessageId.toString());
  }

  private Map<String, Object> evidence(
      CustodyVerification.Outcome outcome,
      String childPersonalCode,
      UUID custodyMessageId,
      UUID childMessageId) {
    Map<String, Object> evidence =
        new LinkedHashMap<>(evidence(outcome, childPersonalCode, custodyMessageId));
    evidence.put("childResponseMessageId", childMessageId.toString());
    return Map.copyOf(evidence);
  }
}
