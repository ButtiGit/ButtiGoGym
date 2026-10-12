package it.pulse.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pulse.workout.ui.PulseColors
import it.pulse.workout.ui.PulseTheme

/** Spiegazione accessibile dal pannello delle autorizzazioni di Health Connect. */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                Surface(color = PulseColors.Background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(20.dp))
                        Text("ButtiGoGym e Health Connect",
                            color = PulseColors.Foreground, fontWeight = FontWeight.ExtraBold,
                            fontSize = 27.sp, lineHeight = 32.sp)
                        Text(
                            "Per mostrare i passi giornalieri, l'avanzamento verso 6.000 passi " +
                            "e gli streak, ButtiGoGym richiede soltanto il permesso di LETTURA dei passi.",
                            color = PulseColors.Foreground
                        )
                        Text(
                            "I passi possono arrivare da Samsung Health e da altre sorgenti " +
                            "collegate a Health Connect. Usiamo i totali aggregati, evitando il doppio conteggio.",
                            color = PulseColors.Foreground
                        )
                        Text(
                            "I dati vengono letti quando usi l'app, memorizzati soltanto sul telefono " +
                            "e non inviati a GitHub, pubblicità o server esterni. Non modifichiamo " +
                            "né scriviamo dati sanitari in Health Connect.",
                            color = PulseColors.Foreground
                        )
                        Text(
                            "Puoi revocare l'accesso in qualsiasi momento da " +
                            "Impostazioni → Health Connect → Autorizzazioni app. " +
                            "ButtiGoGym smette di leggere i passi e rimuove i totali Health Connect " +
                            "memorizzati quando rileva la revoca.",
                            color = PulseColors.Muted, fontSize = 13.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PulseColors.Accent,
                                contentColor = Color.White
                            )
                        ) { Text("Ho capito") }
                    }
                }
            }
        }
    }
}
