package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.mandate.Mandate;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface MandateProcessRepository extends CrudRepository<MandateProcess, Long> {

  public MandateProcess findOneByProcessId(String processId);

  public List<MandateProcess> findAllByMandate(Mandate mandate);
}
