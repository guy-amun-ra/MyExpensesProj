package org.totschnig.myexpenses.util;

import org.junit.Test;

import static junit.framework.Assert.assertTrue;

public class DistribHelperAmazonTest {

  @Test
  public void distributionShouldBeAmazon() {
    assertTrue(DistributionHelper.isAmazon());
  }
}
