package org.totschnig.myexpenses.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(packageName = "org.totschnig.myexpenses")
public class ContribFeatureTest {

  @Test
  public void labelsShouldBeDefinedAsResources() {
    for (ContribFeature contribFeature : ContribFeature.values()) {
      assertThat(contribFeature.getLabelResId(RuntimeEnvironment.application))
          .withFailMessage("label for " + contribFeature + " not defined").isGreaterThan(0);
    }
  }
}
