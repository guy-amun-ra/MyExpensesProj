# Steps for preparing a new release
  
* Set version info in build.gradle
* Check that master is merged into distribution branch
* check if version_codes, version_names, upgrade.xml use the correction version code
* if applicable publish announcement on Google+ and Facebook and add links
* Test and assemble
  * ./gradlew lintPlayWithAdsInternRelease
  * ./gradlew testPlayWithAdsInternDebugUnitTest (-PBETA=true)
  * adb uninstall org.totschnig.myexpenses.debug
  * ./gradlew clean connectedPlayWithAdsInternDebugAndroidTest
  * ./gradlew clean bundlePlayWithAdsInternRelease
* test upgrade mechanism
* execute command returned by ./gradlew playEchoPublishTag
* upload to Play
* add recent changes in Market
* update _config.yml and push gh-pages

# Huawei
* ./gradlew clean myExpenses:packageHuaweiWithAdsInternReleaseUniversalApk
* execute command returned by ./gradlew huaweiEchoPublishTag

# Amazon
# ./gradlew clean :myExpenses:packageAmazonWithAdsInternReleaseUniversalApk
* execute command returned by ./gradlew amazonEchoPublishTag

