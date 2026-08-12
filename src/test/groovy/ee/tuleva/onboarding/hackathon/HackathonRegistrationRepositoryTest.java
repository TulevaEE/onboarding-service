package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.hackathon.HackathonChallenge.COLLECTIVE_BUYING_POWER;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonParticipation.LOOKING_FOR_TEAM;
import static ee.tuleva.onboarding.hackathon.HackathonRole.PARTICIPANT;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.SOFTWARE_DEVELOPMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class HackathonRegistrationRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

  @Autowired HackathonRegistrationRepository repository;
  @Autowired TestEntityManager entityManager;

  private User persistedUser(String personalCode) {
    return entityManager.persistAndFlush(
        User.builder()
            .personalCode(personalCode)
            .firstName("Test")
            .lastName("Person")
            .email(personalCode + "@example.com")
            .createdDate(Instant.parse("2026-08-12T10:00:00Z"))
            .updatedDate(Instant.parse("2026-08-12T10:00:00Z"))
            .active(true)
            .build());
  }

  private HackathonRegistration.HackathonRegistrationBuilder registration(Long userId) {
    return HackathonRegistration.builder()
        .userId(userId)
        .email("participant@example.com")
        .phoneNumber("+37255555555")
        .role(PARTICIPANT)
        .skills(List.of(SOFTWARE_DEVELOPMENT, DATA_AND_AI))
        .challenges(List.of(FAIR_LENDING, COLLECTIVE_BUYING_POWER))
        .participation(LOOKING_FOR_TEAM)
        .idea("Fondiosaku tagatisel krediidiliin")
        .linkedinUrl("https://linkedin.com/in/example")
        .createdTime(NOW)
        .updatedTime(NOW);
  }

  @Test
  void savesAndReadsBackTheSkillAndChallengeLists() {
    var user = persistedUser("38812121215");

    repository.saveAndFlush(registration(user.getId()).build());
    entityManager.clear();

    var found = repository.findByUserId(user.getId()).orElseThrow();
    assertThat(found.getSkills()).containsExactly(SOFTWARE_DEVELOPMENT, DATA_AND_AI);
    assertThat(found.getChallenges()).containsExactly(FAIR_LENDING, COLLECTIVE_BUYING_POWER);
    assertThat(found.getRole()).isEqualTo(PARTICIPANT);
    assertThat(found.getParticipation()).isEqualTo(LOOKING_FOR_TEAM);
    assertThat(found.getCreatedTime()).isNotNull();
    assertThat(found.getUpdatedTime()).isNotNull();
  }

  @Test
  void savesEmptySkillAndChallengeLists() {
    var user = persistedUser("38812121215");

    repository.saveAndFlush(
        registration(user.getId()).skills(List.of()).challenges(List.of()).build());
    entityManager.clear();

    var found = repository.findByUserId(user.getId()).orElseThrow();
    assertThat(found.getSkills()).isEmpty();
    assertThat(found.getChallenges()).isEmpty();
  }

  @Test
  void savesWithoutTheOptionalFields() {
    var user = persistedUser("38812121215");

    repository.saveAndFlush(
        registration(user.getId()).phoneNumber(null).idea(null).linkedinUrl(null).build());
    entityManager.clear();

    var found = repository.findByUserId(user.getId()).orElseThrow();
    assertThat(found.getPhoneNumber()).isNull();
    assertThat(found.getIdea()).isNull();
    assertThat(found.getLinkedinUrl()).isNull();
  }

  @Test
  void allowsOnlyOneRegistrationPerUser() {
    var user = persistedUser("38812121215");
    repository.saveAndFlush(registration(user.getId()).build());

    assertThatThrownBy(() -> repository.saveAndFlush(registration(user.getId()).build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByUserId_withoutARegistration_isEmpty() {
    var user = persistedUser("38812121215");

    assertThat(repository.findByUserId(user.getId())).isEmpty();
  }
}
