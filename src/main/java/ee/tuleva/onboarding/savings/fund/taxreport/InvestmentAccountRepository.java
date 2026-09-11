package ee.tuleva.onboarding.savings.fund.taxreport;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface InvestmentAccountRepository extends CrudRepository<InvestmentAccount, String> {

  @Transactional
  default void declareIban(String personalCode, String iban) {
    if (updateIban(personalCode, iban) == 0 && insertIfAbsent(personalCode, iban) == 0) {
      updateIban(personalCode, iban);
    }
  }

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE investment_account SET iban = :iban WHERE personal_code = :personalCode",
      nativeQuery = true)
  int updateIban(@Param("personalCode") String personalCode, @Param("iban") String iban);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO investment_account (personal_code, iban)
          VALUES (:personalCode, :iban)
          ON CONFLICT DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(@Param("personalCode") String personalCode, @Param("iban") String iban);
}
