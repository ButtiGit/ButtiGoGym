package it.pulse.workout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.R

/** La mascotte parla in una frase: solo guida, senza tap fasulli o contenuto demo. */
@Composable
fun MascotCoachCard(message: String, small: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "Consiglio della mascotte ButtiGoGym: $message"
        },
        color = PulseColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PulseColors.Border)
    ) {
        Row(Modifier.padding(if (small) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.buttigogym_mascot),
                contentDescription = null,
                modifier = Modifier.size(if (small) 48.dp else 64.dp)
                    .clip(RoundedCornerShape(15.dp))
            )
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("IL TUO COACH", color = PulseColors.Accent,
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Text(message, color = PulseColors.Foreground,
                    fontSize = if (small) 12.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = if (small) 17.sp else 20.sp)
            }
        }
    }
}
