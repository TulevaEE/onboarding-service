package ee.tuleva.onboarding.investment.report.publishing.data;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstrumentReferenceRepository extends JpaRepository<InstrumentReference, Long> {

  List<InstrumentReference> findByIsinIn(List<String> isins);
}
