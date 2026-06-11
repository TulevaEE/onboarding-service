package ee.tuleva.onboarding.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CompanyRepresentationRightRepositoryTest {

  @Autowired CompanyRepository companyRepository;
  @Autowired CompanyRepresentationRightRepository repository;

  @Test
  void persistsAndFindsRepresentationRightsByCompany() {
    var company =
        companyRepository.save(Company.builder().registryCode("90000001").name("Test OÜ").build());

    repository.save(
        CompanyRepresentationRight.builder()
            .companyId(company.getId())
            .entryId(12345L)
            .representationType("AINUESINDUS")
            .representationTypeText("Juhatuse liige esindab äriühingut ainuisikuliselt")
            .content("Tehingute tegemiseks on nõutav nõukogu nõusolek")
            .startDate(LocalDate.of(2023, 1, 15))
            .build());

    var rights = repository.findByCompanyId(company.getId());

    assertThat(rights)
        .singleElement()
        .satisfies(
            right -> {
              assertThat(right.getEntryId()).isEqualTo(12345L);
              assertThat(right.getRepresentationType()).isEqualTo("AINUESINDUS");
              assertThat(right.getRepresentationTypeText())
                  .isEqualTo("Juhatuse liige esindab äriühingut ainuisikuliselt");
              assertThat(right.getContent())
                  .isEqualTo("Tehingute tegemiseks on nõutav nõukogu nõusolek");
              assertThat(right.getStartDate()).isEqualTo(LocalDate.of(2023, 1, 15));
              assertThat(right.getEndDate()).isNull();
              assertThat(right.getCreatedDate()).isNotNull();
            });
  }

  @Test
  void deletesRepresentationRightsByCompany() {
    var company =
        companyRepository.save(Company.builder().registryCode("90000002").name("Other OÜ").build());
    repository.save(
        CompanyRepresentationRight.builder().companyId(company.getId()).entryId(1L).build());

    repository.deleteByCompanyId(company.getId());

    assertThat(repository.findByCompanyId(company.getId())).isEmpty();
  }
}
