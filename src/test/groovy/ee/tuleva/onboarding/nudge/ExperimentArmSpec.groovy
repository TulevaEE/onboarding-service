package ee.tuleva.onboarding.nudge

import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.nudge.ExperimentArm.CONTROL
import static ee.tuleva.onboarding.nudge.ExperimentArm.TREATMENT

class ExperimentArmSpec extends Specification {

  static final String SEED = "season-2026-test-seed"

  @Unroll
  def "the bucket of #personalCode is #bucket and the arm at a twenty percent holdout is #arm"() {
    expect:
    ExperimentArm.bucket(personalCode, SEED) == bucket
    ExperimentArm.assign(personalCode, SEED, 20) == arm

    where:
    personalCode  || bucket | arm
    "38888880000" || 40     | TREATMENT
    "38888880046" || 99     | TREATMENT
    "38888880068" || 19     | CONTROL
    "38888880077" || 0      | CONTROL
    "38888880167" || 20     | TREATMENT
  }

  def "surrounding whitespace never moves a person between arms"() {
    expect:
    ExperimentArm.bucket("  38888880068  ", SEED) == 19
    ExperimentArm.assign("  38888880068  ", SEED, 20) == CONTROL
  }

  def "a holdout of zero puts everyone in treatment and a full holdout everyone in control"() {
    expect:
    ExperimentArm.assign("38888880077", SEED, 0) == TREATMENT
    ExperimentArm.assign("38888880046", SEED, 100) == CONTROL
  }

  def "a different seed reshuffles the buckets"() {
    expect:
    ExperimentArm.bucket("38888880000", "another-seed") != 40
  }
}
