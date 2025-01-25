package ee.tuleva.onboarding.aml.risklevel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmlRiskRepositoryService {

  private final AmlRiskMetadataRepository amlRiskMetadataRepository;
  private final ObjectMapper objectMapper;

  public List<RiskLevelResult> getHighRiskRows() {
    List<AmlRiskMetadata> data = amlRiskMetadataRepository.findAllByRiskLevel(1);
    return data.stream().map(this::toRiskLevelResult).collect(Collectors.toList());
  }

  private RiskLevelResult toRiskLevelResult(AmlRiskMetadata amlRisk) {
    Map<String, Object> metadataMap = parseJsonMetadata(amlRisk.getMetadata());
    return new RiskLevelResult(amlRisk.getPersonalId(), amlRisk.getRiskLevel(), metadataMap);
  }

  private Map<String, Object> parseJsonMetadata(String jsonStr) {
    if (jsonStr == null) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(jsonStr, new TypeReference<>() {});
    } catch (Exception e) {
      log.error("Risk level metadata serialization error {}", jsonStr);
      return Map.of();
    }
  }
}
