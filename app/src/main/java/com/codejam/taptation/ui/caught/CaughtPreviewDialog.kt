package com.codejam.taptation.ui.caught

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.logic.meadow.MeadowCameo
import com.codejam.taptation.ui.common.CatchChoiceDialog
import com.codejam.taptation.ui.theme.TaptationTheme

/** Debug fixtures only: no catch, health write, unlock, or original-app launch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaughtPreviewDialog(onClose:()->Unit) {
    var cameo by remember{mutableStateOf(MeadowCameo.Fish)}
    val initialDark=MaterialTheme.colorScheme.background.luminance()<.5f
    var dark by remember{mutableStateOf(initialDark)}
    var showing by remember{mutableStateOf(false)}
    var replay by remember{mutableIntStateOf(0)}
    var result by remember{mutableStateOf("")}
    TaptationTheme(darkTheme=dark) {
        Dialog(onDismissRequest=onClose){Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.background){
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("Catch preview",style=MaterialTheme.typography.headlineSmall)
                Text("Preview only. Your health and discoveries do not change.",style=MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    MeadowCameo.entries.forEach{scene->FilterChip(selected=cameo==scene,onClick={cameo=scene},label={Text(scene.name)})}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Dark preview");Switch(checked=dark,onCheckedChange={dark=it})}
                Button(onClick={replay++;showing=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Play catch preview")}
                if(result.isNotEmpty())Text(result,style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onClose,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("Close")}
            }
        }}
        if(showing)key(replay){
            fun finish(label:String){result="Preview: $label";showing=false}
            CatchChoiceDialog(CatchDetails("com.instagram.android",90,80,10,"app_always_bait",null),"Instagram",
                onTaptation={finish("Go to Taptation")},onOriginal={finish("Continue to Instagram")},
                onLater={finish("Dismissed")},
                continueExtraDamage=5,cameo=cameo,sceneKey="debug-$replay")
        }
    }
}
