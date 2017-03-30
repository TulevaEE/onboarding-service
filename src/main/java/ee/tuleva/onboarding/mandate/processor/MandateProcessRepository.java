package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.mandate.Mandate;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface MandateProcessRepository extends CrudRepository<MandateProcess, Long> {

    public MandateProcess findOneByProcessId(String processId);

    public List<MandateProcess> findAllByMandate(Mandate mandate);
}
