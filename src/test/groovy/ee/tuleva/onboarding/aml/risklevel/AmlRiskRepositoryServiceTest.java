package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.PERSON_ID_3;
import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.PERSON_ID_7;
import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.PERSON_ID_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AmlRiskRepositoryServiceTest {

  private AmlRiskMetadataRepository repository;
  private AmlRiskRepositoryService service;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(AmlRiskMetadataRepository.class);
    objectMapper = new ObjectMapper();
    service = new AmlRiskRepositoryService(repository, objectMapper);
  }

  @Test
  @DisplayName("Should return an empty list when no high-risk rows are found")
  void getHighRiskRows_noResults() {
    when(repository.findAllByRiskLevel(1)).thenReturn(List.of());
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertTrue(results.isEmpty());
  }

  @Test
  @DisplayName("Should parse valid JSON metadata correctly")
  void getHighRiskRows_validJson() {
    AmlRiskMetadata row =
        new AmlRiskMetadata(
            PERSON_ID_3, 1, "{\"attribute_1\":5,\"attribute_2\":3,\"risk_level\":1}");
    when(repository.findAllByRiskLevel(1)).thenReturn(List.of(row));
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());
    RiskLevelResult r = results.get(0);
    assertEquals(PERSON_ID_3, r.getPersonalId());
    assertEquals(1, r.getRiskLevel());
    assertEquals(5, r.getMetadata().get("attribute_1"));
    assertEquals(3, r.getMetadata().get("attribute_2"));
  }

  @Test
  @DisplayName("Should handle null metadata by returning an empty map")
  void getHighRiskRows_nullMetadata() {
    AmlRiskMetadata row = new AmlRiskMetadata(PERSON_ID_7, 1, null);
    when(repository.findAllByRiskLevel(1)).thenReturn(List.of(row));
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());
    assertTrue(results.get(0).getMetadata().isEmpty());
  }

  @Test
  @DisplayName("Should handle invalid JSON by returning an empty map")
  void getHighRiskRows_invalidJson() {
    AmlRiskMetadata row = new AmlRiskMetadata(PERSON_ID_8, 1, "{bad-json:??");
    when(repository.findAllByRiskLevel(1)).thenReturn(List.of(row));
    List<RiskLevelResult> results = service.getHighRiskRows();
    assertEquals(1, results.size());
    assertTrue(results.get(0).getMetadata().isEmpty());
  }
}
