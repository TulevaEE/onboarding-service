package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.mandate.Mandate;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface MandateProcessRepository extends CrudRepository<MandateMessageProcess, Long> {

    public MandateMessageProcess findOneByProcessId(String processId);

    public List<MandateMessageProcess> findAllByMandate(Mandate mandate);
}
