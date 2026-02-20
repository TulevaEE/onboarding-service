package ee.tuleva.onboarding.user.member;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class MemberRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private MemberRepository repository;

  @BeforeEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void returnsNextMemberNumber() {
    // given
    User persistedUser = entityManager.persist(sampleUser());
    Member member = Member.builder().user(persistedUser).memberNumber(9999).build();
    entityManager.persist(member);
    entityManager.flush();

    // when
    Integer maxMemberNumber = repository.getNextMemberNumber();

    // then
    assertThat(maxMemberNumber).isEqualTo(10000);
  }

  @Test
  void persistingANewMemberGeneratesTheCreatedDateField() {
    // given
    User persistedUser = entityManager.persist(sampleUser());
    Member member = Member.builder().user(persistedUser).memberNumber(1234).build();

    // when
    Member persistedMember = repository.save(member);

    // then
    assertThat(persistedMember.getCreatedDate()).isNotNull();
  }

  @Test
  void onlyReturnsActiveMembers() {
    // given
    User user1 = entityManager.persist(sampleUser("60001019906", "a@b.ee"));
    User user2 = entityManager.persist(sampleUser("39201070898", "c@d.ee"));
    entityManager.persist(Member.builder().user(user1).memberNumber(123).active(true).build());
    entityManager.persist(Member.builder().user(user2).memberNumber(234).active(false).build());
    entityManager.flush();

    // when
    List<Member> members = (List<Member>) repository.findAll();

    // then
    assertThat(members).hasSize(1);
    assertThat(members.get(0).getActive()).isTrue();
    assertThat(members.get(0).getMemberNumber()).isEqualTo(123);
  }

  @Test
  void findByUserPersonalCode_returnsMemberWhenFound() {
    // given
    String personalCode = "38801010000";
    User user = entityManager.persist(sampleUser(personalCode, "test@test.com"));
    Member member = Member.builder().user(user).memberNumber(555).build();
    entityManager.persistAndFlush(member);

    // when
    Optional<Member> foundMember = repository.findByUserPersonalCode(personalCode);

    // then
    assertThat(foundMember).isPresent();
    assertThat(foundMember.get().getId()).isEqualTo(member.getId());
    assertThat(foundMember.get().getMemberNumber()).isEqualTo(555);
  }

  @Test
  void findByUserPersonalCode_returnsEmptyWhenNotFound() {
    // given
    String personalCode = "38812121215";

    // when
    Optional<Member> foundMember = repository.findByUserPersonalCode(personalCode);

    // then
    assertThat(foundMember).isNotPresent();
  }

  private User sampleUser() {
    return sampleUser("37605030299", "sample.user@tuleva.ee");
  }

  private User sampleUser(String personalCode, String email) {
    return User.builder()
        .personalCode(personalCode)
        .email(email)
        .firstName("First")
        .lastName("Last")
        .build();
  }
}
