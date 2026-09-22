package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.INSURANCE;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DESIGN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class HackathonIdeaRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

  @Autowired HackathonIdeaRepository repository;
  @Autowired TestEntityManager entityManager;

  private User persistedUser() {
    return entityManager.persistAndFlush(
        sampleUser().id(null).member(null).personalCode("39001109103").build());
  }

  private HackathonIdea.HackathonIdeaBuilder idea(Long userId) {
    return HackathonIdea.builder()
        .userId(userId)
        .challenge(FAIR_LENDING)
        .problem("Laenu vahetamine on tülikas")
        .solution("Fondiosaku tagatisel krediidiliin")
        .progress("Prototüüp on olemas")
        .neededSkills(List.of(DESIGN, DATA_AND_AI))
        .additionalInfo("Otsin arendajat")
        .createdTime(NOW);
  }

  @Test
  void savesAndReadsBackTheIdea() {
    var user = persistedUser();

    var saved = repository.saveAndFlush(idea(user.getId()).build());
    entityManager.clear();

    assertThat(repository.findById(saved.getId()))
        .contains(idea(user.getId()).id(saved.getId()).build());
  }

  @Test
  void savesWithoutTheOptionalFields() {
    var user = persistedUser();

    var saved =
        repository.saveAndFlush(
            idea(user.getId()).progress(null).additionalInfo(null).neededSkills(List.of()).build());
    entityManager.clear();

    var found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getProgress()).isNull();
    assertThat(found.getAdditionalInfo()).isNull();
    assertThat(found.getNeededSkills()).isEmpty();
  }

  @Test
  void findAllByUserId_returnsTheUsersIdeasOldestFirst() {
    var user = persistedUser();
    var later =
        repository.saveAndFlush(
            idea(user.getId()).challenge(INSURANCE).createdTime(NOW.plusSeconds(60)).build());
    var earlier = repository.saveAndFlush(idea(user.getId()).build());
    entityManager.clear();

    assertThat(repository.findAllByUserIdOrderByCreatedTimeAsc(user.getId()))
        .extracting(HackathonIdea::getId)
        .containsExactly(earlier.getId(), later.getId());
  }
}
