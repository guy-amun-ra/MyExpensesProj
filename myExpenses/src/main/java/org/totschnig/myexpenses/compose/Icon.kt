package org.totschnig.myexpenses.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.viewmodel.data.ExtraIcon
import org.totschnig.myexpenses.viewmodel.data.IIconInfo
import org.totschnig.myexpenses.viewmodel.data.IconInfo

@Composable
fun Icon(icon: String, size: Dp = 24.dp) {
    val iconInfo = IIconInfo.resolveIcon(icon)
    if (iconInfo == null) {
        Text(color = Color.Red, text = icon)
    } else {
        Icon(iconInfo, size)
    }
}

@Composable
fun Icon(iconInfo: IIconInfo, size: Dp = 24.dp) {
    when (iconInfo) {
        is ExtraIcon -> {
            androidx.compose.material.Icon(
                modifier = Modifier.size(size * 1.25f),
                painter = painterResource(iconInfo.drawable),
                contentDescription = stringResource(id = iconInfo.label)
            )
        }
        is IconInfo -> {
            CharIcon(char = iconInfo.unicode, font = iconInfo.font)
        }
    }
}
@Composable
fun CharIcon(char: Char, font: Int? = null, size: Dp = 24.dp) {
    Text(
        text = char.toString(),
        fontFamily = font?.let { remember { FontFamily(Font(it, FontWeight.Normal)) } },
        fontSize = with(LocalDensity.current) { size.toSp() }
    )
}

@Preview
@Composable
fun IconTest() {
    Icon(icon = "apple")
}