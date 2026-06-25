package ee.tuleva.onboarding.party;

import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.CHILD_NOT_ALIVE;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NOT_ASSET_MANAGEMENT;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.NO_CUSTODY;
import static ee.tuleva.onboarding.party.CustodyVerification.Outcome.OK;

import ee.tuleva.onboarding.populationregister.CustodyRight;
import ee.tuleva.onboarding.populationregister.PopulationRegisterClient;
import ee.tuleva.onboarding.populationregister.PopulationRegisterPerson;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustodyVerificationService {

  private final PopulationRegisterClient populationRegisterClient;

  public CustodyVerification verify(String parentPersonalCode, String childPersonalCode) {
    CustodyRight custody =
        populationRegisterClient.fetchCustodyRights(parentPersonalCode).stream()
            .filter(right -> right.childPersonalCode().equals(childPersonalCode))
            .findFirst()
            .orElse(null);

    if (custody == null) {
      log.info(
          "No custody record for child: parentCode={}, childCode={}",
          parentPersonalCode,
          childPersonalCode);
      return CustodyVerification.notVerified(NO_CUSTODY);
    }
    if (!custody.childAlive()) {
      return CustodyVerification.notVerified(CHILD_NOT_ALIVE);
    }
    if (!custody.grantsAssetManagement()) {
      log.info(
          "Custody does not grant asset management: parentCode={}, childCode={}, type={}",
          parentPersonalCode,
          childPersonalCode,
          custody.type());
      return CustodyVerification.notVerified(NOT_ASSET_MANAGEMENT);
    }

    PopulationRegisterPerson child = populationRegisterClient.fetchPerson(childPersonalCode);
    if (!child.isAlive()) {
      return CustodyVerification.notVerified(CHILD_NOT_ALIVE);
    }
    return new CustodyVerification(OK, child, evidence(custody));
  }

  private Map<String, Object> evidence(CustodyRight custody) {
    return Map.of(
        "custodyType", custody.type().name(),
        "valid", custody.valid(),
        "childAlive", custody.childAlive(),
        "childPersonalCode", custody.childPersonalCode());
  }
}
