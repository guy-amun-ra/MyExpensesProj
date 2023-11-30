package org.totschnig.myexpenses.feature

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.reflect.KClass

class FeatureTest {
    @Test
    fun getAllFeatures() {
        val allFeatures = Feature.values
        assertThat(allFeatures).containsExactlyElementsIn(
            getAllObjectInstances(Feature::class)
        )
        allFeatures.forEach {
            assertThat(Feature.fromModuleName(it.moduleName)).isEqualTo(it)
        }
    }
}


fun getAllObjectInstances(sealedClass: KClass<out Feature>): List<Feature> =
    sealedClass.sealedSubclasses.flatMap { member ->
    member.objectInstance?.let { listOf(it) } ?: getAllObjectInstances(member)
}