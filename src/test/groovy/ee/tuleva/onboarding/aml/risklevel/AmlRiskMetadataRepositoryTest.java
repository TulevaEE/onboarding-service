package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.AmlRiskTestDataFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AmlRiskMetadataRepositoryTest {

  @Autowired AmlRiskMetadataRepository repository;
  @Autowired DataSource dataSource;

  @BeforeAll
  static void createSchema(@Autowired DataSource dataSource) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_AML_RISK_SHCEMA);
      stmt.execute(CREATE_AML_RISK_VIEW);
      stmt.execute(CREATE_AML_RISK_METADATA_VIEW);
    }
  }

  @BeforeEach
  void cleanUp() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(TRUNCATE_AML_RISK);
    }
  }

  @Test
  @DisplayName("Should return an empty list when no rows match the given risk level")
  void testFindAllByRiskLevel_noRows_returnsEmptyList() {
    List<AmlRiskMetadata> result = repository.findAllByRiskLevel(1);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Should return rows only for the specified risk level")
  void testFindAllByRiskLevel_matchesOnlyTargetLevel() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_1_RISK_LEVEL_1);
      stmt.execute(INSERT_PERSON_2_RISK_LEVEL_2);
    }
    List<AmlRiskMetadata> level1 = repository.findAllByRiskLevel(1);
    var first = level1.getFirst();
    assertEquals(1, level1.size());
    assertEquals(PERSON_ID_1, first.getPersonalId());
    assertEquals(1, first.getRiskLevel());
    List<AmlRiskMetadata> level2 = repository.findAllByRiskLevel(2);
    var second = level2.getFirst();
    assertEquals(1, level2.size());
    assertEquals(PERSON_ID_2, second.getPersonalId());
    assertEquals(2, second.getRiskLevel());
  }

  @Test
  @DisplayName("Should return all available rows with findAll()")
  void testFindAll() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(INSERT_PERSON_3_RISK_LEVEL_1);
    }
    List<AmlRiskMetadata> all = repository.findAll();
    assertEquals(1, all.size());
    assertEquals(PERSON_ID_3, all.get(0).getPersonalId());
    assertEquals(1, all.get(0).getRiskLevel());
  }
}
