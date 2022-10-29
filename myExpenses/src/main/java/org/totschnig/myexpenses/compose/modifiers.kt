package org.totschnig.myexpenses.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

fun Modifier.conditional(condition : Boolean, block : Modifier.() -> Modifier) = if (condition) {
        then(block(Modifier))
    } else {
        this
    }

@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.conditionalComposed(condition : Boolean, block : @Composable Modifier.() -> Modifier)  =
    composed {
        if (condition) {
            then(block(Modifier))
        } else {
            this
        }
    }

fun Modifier.size(spSize: TextUnit) = composed { this.size(with(LocalDensity.current) { spSize.toDp() }) }