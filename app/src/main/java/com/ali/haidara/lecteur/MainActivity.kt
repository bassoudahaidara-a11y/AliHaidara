package com.ali.haidara.lecteur

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val Navy = Color(0xFF0F1B3D)
private val CardBlue = Color(0xFF1B2A5C)
private val Orange = Color(0xFFFFA726)
private val Teal = Color(0xFF26C6DA)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var voiceStatus by mutableIntStateOf(0) // 0 en attente, 1 prête, 2 voix française manquante
    private var chunks by mutableStateOf(listOf<String>())
    private var index by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var speaking by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var rawText = ""
    private var pendingUri: Uri? = null
    private lateinit var prefs: SharedPreferences

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingUri
        if (ok && u != null) process(u, false)
    }
    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) process(uri, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ali", Context.MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        setContent { App() }
    }

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) { voiceStatus = 2; return }
        val r = t.setLanguage(Locale.FRANCE)
        voiceStatus = if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) 2 else 1
        t.setSpeechRate(prefs.getFloat("rate", 0.85f))
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { runOnUiThread { speaking = true } }
            override fun onDone(id: String?) { runOnUiThread { speaking = false } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { runOnUiThread { speaking = false } }
        })
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    private fun speakCurrent() {
        val t = chunks.getOrNull(index) ?: return
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "chunk")
    }

    private fun stopSpeaking() { tts?.stop(); speaking = false }

    private fun openCamera() {
        val dir = File(cacheDir, "images").apply { mkdirs() }
        val f = File(dir, "p_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        pendingUri = uri
        takePicture.launch(uri)
    }

    private fun newBook() { stopSpeaking(); rawText = ""; chunks = emptyList(); index = 0; message = null }

    private fun process(uri: Uri, isPdf: Boolean) {
        busy = true; message = null
        lifecycleScope.launch {
            val text = try {
                if (isPdf) Ocr.fromPdf(this@MainActivity, uri) else Ocr.fromImage(this@MainActivity, uri)
            } catch (e: Exception) { "" }
            if (text.isBlank()) {
                message = "Je n'ai pas réussi à lire le texte. Essaie avec une photo plus nette, bien éclairée."
                busy = false
                return@launch
            }
            rawText = (rawText + "\n\n" + text).trim()
            val key = prefs.getString("groq", "") ?: ""
            val better = if (key.isNotBlank() && rawText.length <= 6000) Groq.simplify(key, rawText) else null
            chunks = better ?: Chunker.split(rawText)
            if (index >= chunks.size) index = 0
            busy = false
        }
    }

    @Composable
    private fun App() {
        var showSettings by remember { mutableStateOf(false) }
        MaterialTheme(colorScheme = darkColorScheme(primary = Orange, background = Navy, surface = CardBlue)) {
            Surface(Modifier.fillMaxSize(), color = Navy) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                    Header { showSettings = true }
                    Spacer(Modifier.height(16.dp))
                    when {
                        busy -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Orange)
                                Spacer(Modifier.height(16.dp))
                                Text("Je lis ton cahier…", color = Color.White, fontSize = 20.sp)
                            }
                        }
                        chunks.isEmpty() -> Home()
                        else -> Reader()
                    }
                }
                if (showSettings) Settings { showSettings = false }
            }
        }
    }

    @Composable
    private fun Header(onSettings: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Orange),
                contentAlignment = Alignment.Center
            ) { Text("AH", color = Navy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Ali Haïdara", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("⚙️", fontSize = 24.sp) }
        }
    }

    @Composable
    private fun Home() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Bonjour Ali 👋", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Filme ton cahier ou choisis un PDF.\nJe le coupe en petits morceaux et je te le lis.",
                color = Color(0xFFB0BEE8), fontSize = 18.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            BigButton("📷  Filmer mon cahier", Orange, Navy) { openCamera() }
            Spacer(Modifier.height(16.dp))
            BigButton("📄  Choisir un PDF", Teal, Navy) { pickPdf.launch(arrayOf("application/pdf")) }
            message?.let {
                Spacer(Modifier.height(20.dp))
                Text(it, color = Color(0xFFFF8A80), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            if (voiceStatus == 2) {
                Spacer(Modifier.height(20.dp))
                Text("La voix française n'est pas installée sur ce téléphone.", color = Color(0xFFFFE082), textAlign = TextAlign.Center)
                TextButton(onClick = {
                    try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } catch (e: Exception) {
                        message = "Ouvre les réglages du téléphone > Synthèse vocale pour installer le français."
                    }
                }) { Text("Installer la voix française", color = Orange, fontSize = 16.sp) }
            }
        }
    }

    @Composable
    private fun Reader() {
        val last = index == chunks.lastIndex
        Column(Modifier.fillMaxSize()) {
            Text("Morceau ${index + 1} sur ${chunks.size}", color = Color(0xFFB0BEE8), fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (index + 1f) / chunks.size },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Orange, trackColor = CardBlue
            )
            Spacer(Modifier.height(16.dp))
            Card(
                Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBlue)
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text(chunks[index], color = Color.White, fontSize = 26.sp, lineHeight = 36.sp)
                    if (last) {
                        Spacer(Modifier.height(20.dp))
                        Text("Bravo ! C'est le dernier morceau 🎉", color = Orange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { if (index > 0) { index--; speakCurrent() } },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, Teal)
                ) { Text("◀", color = Teal, fontSize = 22.sp) }
                Button(
                    onClick = { if (speaking) stopSpeaking() else speakCurrent() },
                    modifier = Modifier.weight(2f).height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) { Text(if (speaking) "⏹ Stop" else "🔊 Lire", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { if (!last) { index++; speakCurrent() } },
                    enabled = !last,
                    modifier = Modifier.weight(1.5f).height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) { Text("Suivant ▶", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { openCamera() }) { Text("➕ Photo", color = Color(0xFFB0BEE8)) }
                TextButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("➕ PDF", color = Color(0xFFB0BEE8)) }
                TextButton(onClick = { newBook() }) { Text("🆕 Nouveau", color = Color(0xFFB0BEE8)) }
            }
        }
    }

    @Composable
    private fun BigButton(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(70.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = bg)
        ) { Text(label, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }

    @Composable
    private fun Settings(onClose: () -> Unit) {
        var key by remember { mutableStateOf(prefs.getString("groq", "") ?: "") }
        var rate by remember { mutableFloatStateOf(prefs.getFloat("rate", 0.85f)) }
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("groq", key.trim()).putFloat("rate", rate).apply()
                    tts?.setSpeechRate(rate)
                    onClose()
                }) { Text("Enregistrer", color = Orange) }
            },
            title = { Text("Réglages") },
            text = {
                Column {
                    Text("Vitesse de la voix")
                    Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.5f..1.3f)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        label = { Text("Clé API Groq (facultatif)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sans clé, tout marche hors ligne. Avec une clé et internet, le texte est mieux corrigé.",
                        fontSize = 13.sp, color = Color(0xFFB0BEE8)
                    )
                }
            }
        )
    }
}
