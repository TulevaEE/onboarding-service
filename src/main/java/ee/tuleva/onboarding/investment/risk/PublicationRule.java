package ee.tuleva.onboarding.investment.risk;

import java.util.List;

interface PublicationRule {
  PublishedSeries publish(List<ReferencePoint> points);
}
