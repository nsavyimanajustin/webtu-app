package com.example.webtumeals.trial

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrialExpiredScreen(
    onDeveloperUnlocked: () -> Unit = {}
) {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }
    var showDevDialog by remember { mutableStateOf(false) }
    var devPin by remember { mutableStateOf("") }
    var devError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Lock Icon with multi-tap developer bypass (7 taps)
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Verrouillé",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(72.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        tapCount++
                        if (tapCount >= 7) {
                            showDevDialog = true
                            tapCount = 0
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Période d'évaluation terminée",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                text = "انتهاء فترة التجربة",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Cette version de démonstration (7 jours) est arrivée à expiration. Conformément aux mesures de sécurité, de confidentialité et de limitation d'usage, l'application a été désactivée et toutes ses données locales ont été définitivement purgées.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Actions card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Actions exécutées automatiquement :",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ActionItem(text = "Identifiants et mots de passe locaux effacés")
                    ActionItem(text = "Tâches d'arrière-plan et réveils annulés")
                    ActionItem(text = "Fichiers temporaires et cache nettoyés")
                    ActionItem(text = "Trafic réseau vers les serveurs ONOU suspendu")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Avis : WebTU Repas est un projet d'expérimentation indépendant et non-officiel, non affilié à l'ONOU ni au MESRS. Merci de votre participation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 2.4: Uninstall Button
            Button(
                onClick = { TrialManager.promptUninstall(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Désinstaller l'application",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { (context as? Activity)?.finishAffinity() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quitter")
            }
        }
    }

    // Developer private unlock dialog (Justin's personal self-use)
    if (showDevDialog) {
        AlertDialog(
            onDismissRequest = {
                showDevDialog = false
                devPin = ""
                devError = false
            },
            title = { Text("Mode Développeur Privé") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Entrez le code d'accès privé pour réactiver l'application en usage personnel exclusif :",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = devPin,
                        onValueChange = {
                            devPin = it
                            devError = false
                        },
                        label = { Text("Code PIN / Clé") },
                        isError = devError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (devError) {
                        Text("Code incorrect", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (devPin.trim() == "2026" || devPin.trim().lowercase() == "justin") {
                            TrialManager.unlockDeveloperMode(context)
                            showDevDialog = false
                            onDeveloperUnlocked()
                        } else {
                            devError = true
                        }
                    }
                ) {
                    Text("Débloquer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDevDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun ActionItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
