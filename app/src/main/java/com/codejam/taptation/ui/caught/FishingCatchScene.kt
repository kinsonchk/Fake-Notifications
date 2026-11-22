package com.codejam.taptation.ui.caught

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import com.codejam.taptation.logic.meadow.MeadowCameo

@Composable
internal fun FishingCatchScene(cameo:MeadowCameo,appLabel:String?,sceneKey:String,animate:Boolean) {
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    AndroidView(factory={FishingSceneView(it)},modifier=Modifier.fillMaxWidth().height(160.dp).testTag("caught-scene-${cameo.name}"),
        update={it.bind(sceneKey,cameo,appLabel,dark,animate)})
}
