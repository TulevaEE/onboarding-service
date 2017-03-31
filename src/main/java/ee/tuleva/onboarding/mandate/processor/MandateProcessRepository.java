package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.mandate.Mandate;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface MandateProcessRepository extends CrudRepository<MeandateMessageProcess, Long> {

    public MeandateMessageProcess findOneByProcessId(String processId);

    public List<MeandateMessageProcess> findAllByMandate(Mandate mandate);
}
