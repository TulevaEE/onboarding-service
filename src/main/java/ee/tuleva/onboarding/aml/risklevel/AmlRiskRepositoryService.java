package ee.tuleva.onboarding.aml.risklevel;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmlRiskRepositoryService {

  private final AmlRiskMetadataRepository amlRiskMetadataRepository;

  public List<RiskLevelResult> getHighRiskRows() {
    return amlRiskMetadataRepository.findAllByRiskLevel(1).stream()
        .map(
            amlRisk -> {
              Map<String, Object> metadata =
                  amlRisk.getMetadata() == null ? Map.of() : amlRisk.getMetadata();
              return new RiskLevelResult(amlRisk.getPersonalId(), amlRisk.getRiskLevel(), metadata);
            })
        .toList();
  }
}
