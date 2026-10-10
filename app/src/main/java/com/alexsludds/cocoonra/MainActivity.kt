package com.alexsludds.cocoonra

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ---- Look: mirrors Cocoon's theme tokens (background/card gradients, accent gradient, tiles). Tweak to match your theme.
data class Palette(val bg: List<Color>, val card: List<Color>, val text: Color, val sub: Color, val accent: List<Color>, val tile: Color, val gold: Color)
val Dark = Palette(listOf(Color(0xFF1C1D24), Color(0xFF111217)), listOf(Color(0xFF2B2C35), Color(0xFF22232B)),
    Color(0xFFF1F1F5), Color(0xFF9EA1AD), listOf(Color(0xFF8C7BF7), Color(0xFF52C1F5)), Color(0x1AFFFFFF), Color(0xFFF5C451))
val Light = Palette(listOf(Color(0xFFF4F3F8), Color(0xFFE6E5EE)), listOf(Color.White, Color(0xFFF6F6FA)),
    Color(0xFF1C1B22), Color(0xFF6C6B77), listOf(Color(0xFF7363EE), Color(0xFF2FA8E6)), Color(0x12000000), Color(0xFFD9A21B))
val LocalP = staticCompositionLocalOf { Dark }

// ---- RetroAchievements web API
const val MEDIA = "https://media.retroachievements.org"
fun JSONObject.k(n: String): Any? = keys().asSequence().firstOrNull { it.equals(n, true) }?.let { opt(it) }?.takeIf { it != JSONObject.NULL }
fun JSONObject.s(n: String) = k(n)?.toString() ?: ""
fun JSONObject.i(n: String) = s(n).toDoubleOrNull()?.toInt() ?: 0

data class Ach(val id: Int, val title: String, val desc: String, val points: Int, val badge: String, val earned: String?, val hardcore: Boolean, val order: Int, val pct: Int, val missable: Boolean)
data class Game(val id: Int, val title: String, val console: String, val icon: String, val presence: String, val achs: List<Ach>) {
    val earned get() = achs.count { it.earned != null }
    val earnedHc get() = achs.count { it.hardcore }
}
data class Comment(val user: String, val date: String, val text: String)

class Ra(private val user: String, private val key: String) {
    private fun get(ep: String, vararg q: Pair<String, Any>): JSONObject {
        val qs = (q.toList() + ("y" to key)).joinToString("&") { "${it.first}=${URLEncoder.encode(it.second.toString(), "UTF-8")}" }
        val c = URL("https://retroachievements.org/API/API_$ep.php?$qs").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "CocoonRA/1.0")
        if (c.responseCode != 200) error("RetroAchievements returned ${c.responseCode}. Check your username and API key.")
        return JSONObject(c.inputStream.bufferedReader().readText())
    }

    fun current(): Game {
        val p = get("GetUserProfile", "u" to user)
        val g = get("GetGameInfoAndUserProgress", "g" to p.i("LastGameID"), "u" to user, "a" to 1)
        val players = maxOf(1, g.i("NumDistinctPlayers"), g.i("NumDistinctPlayersCasual"))
        val list = (g.k("Achievements") as? JSONObject)?.let { o -> o.keys().asSequence().map { o.getJSONObject(it) }.map { a ->
            val hc = a.s("DateEarnedHardcore").ifEmpty { null }
            Ach(a.i("ID"), a.s("Title"), a.s("Description"), a.i("Points"), a.s("BadgeName"), hc ?: a.s("DateEarned").ifEmpty { null },
                hc != null, a.i("DisplayOrder"), a.i("NumAwarded") * 100 / players, a.s("Type") == "missable")
        }.sortedBy { it.order }.toList() } ?: emptyList()
        return Game(g.i("ID"), g.s("Title"), g.s("ConsoleName"), MEDIA + g.s("ImageIcon"), p.s("RichPresenceMsg"), list)
    }

    fun comments(achId: Int): List<Comment> {
        val r = get("GetComments", "i" to achId, "t" to 2, "sort" to "-submitted", "c" to 100).k("Results") as? org.json.JSONArray ?: return emptyList()
        return (0 until r.length()).map { r.getJSONObject(it) }.map {
            Comment(it.s("User"), it.s("Submitted").take(10), Html.fromHtml(it.s("CommentText"), 0).toString().trim())
        }
    }
}

// ---- App
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("ra", MODE_PRIVATE)
        setContent {
            val p = if (isSystemInDarkTheme()) Dark else Light
            val scheme = if (p == Dark) darkColorScheme(primary = p.accent[0]) else lightColorScheme(primary = p.accent[0])
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalP provides p, LocalContentColor provides p.text) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(p.bg)).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        App(prefs)
                    }
                }
            }
        }
    }
}

@Composable
fun App(prefs: android.content.SharedPreferences) {
    var user by remember { mutableStateOf(prefs.getString("u", "")!!) }
    var key by remember { mutableStateOf(prefs.getString("k", "")!!) }
    var editing by remember { mutableStateOf(user.isEmpty() || key.isEmpty()) }
    var game by remember { mutableStateOf<Game?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Ach?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(user, key, editing, tick) {
        if (editing) return@LaunchedEffect
        while (true) { // keep polling so new unlocks appear while you play on the top screen
            try { game = withContext(Dispatchers.IO) { Ra(user, key).current() }; err = null }
            catch (e: Exception) { err = e.message ?: "Couldn't reach RetroAchievements" }
            delay(30_000)
        }
    }
    BackHandler(open != null) { open = null }

    when {
        editing -> Setup(user, key) { u, k -> prefs.edit().putString("u", u).putString("k", k).apply(); user = u; key = k; editing = false }
        open != null -> Detail(open!!, Ra(user, key)) { open = null }
        else -> Main(game, err, onRefresh = { tick++ }, onSettings = { editing = true }, onOpen = { open = it })
    }
}

@Composable
fun Main(game: Game?, err: String?, onRefresh: () -> Unit, onSettings: () -> Unit, onOpen: (Ach) -> Unit) {
    val p = LocalP.current
    var filter by remember { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Achievements", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconTile(Icons.Default.Refresh, onRefresh); Spacer(Modifier.width(8.dp)); IconTile(Icons.Default.Settings, onSettings)
            }
        }
        if (err != null) item { Text(err, color = p.sub, modifier = Modifier.fillMaxWidth().card().padding(16.dp)) }
        if (game == null) { if (err == null) item { Text("Loading…", color = p.sub) }; return@LazyColumn }
        item { GameCard(game) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Locked", "Unlocked", "Missable").forEachIndexed { i, t -> Pill(t, filter == i) { filter = i } }
            }
        }
        val shown = game.achs.filter { when (filter) { 1 -> it.earned == null; 2 -> it.earned != null; 3 -> it.missable; else -> true } }
        if (shown.isEmpty()) item { Text(if (filter == 3) "No achievements in this set are marked missable." else "Nothing here.", color = p.sub) }
        items(shown, key = { it.id }) { AchRow(it) { onOpen(it) } }
    }
}

@Composable
fun GameCard(g: Game) {
    val p = LocalP.current
    val total = g.achs.size
    val frac = if (total == 0) 0f else g.earned / total.toFloat()
    Column(Modifier.fillMaxWidth().card().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(g.icon, null, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(g.console, color = p.sub, fontSize = 13.sp)
                if (g.presence.isNotBlank()) Text(g.presence, color = p.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${g.earned}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(" / $total", color = p.sub, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp).weight(1f))
            val label = when { total == 0 -> "No set"; g.earnedHc == total -> "Mastered"; g.earned == total -> "Completed"; else -> "${(frac * 100).toInt()}%" }
            Text(label, fontWeight = FontWeight.SemiBold, color = if (g.earned == total && total > 0) p.gold else p.text)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(p.tile)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(p.accent)))
        }
        Spacer(Modifier.height(8.dp))
        val pts = g.achs.filter { it.earned != null }.sumOf { it.points }
        Text("$pts / ${g.achs.sumOf { it.points }} points · ${g.earnedHc} hardcore", color = p.sub, fontSize = 12.sp)
    }
}

@Composable
fun AchRow(a: Ach, onClick: () -> Unit) {
    val p = LocalP.current
    Row(Modifier.fillMaxWidth().card(18.dp).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AchBadge(a, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.desc, color = p.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull("Missable".takeIf { a.missable }, a.earned?.let { "Unlocked ${it.take(10)}" }, "${a.pct}% of players").joinToString(" · "), color = p.sub, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Points(a)
    }
}

@Composable
fun Detail(a: Ach, ra: Ra, onBack: () -> Unit) {
    val p = LocalP.current
    val ctx = LocalContext.current
    var comments by remember(a.id) { mutableStateOf<List<Comment>?>(null) }
    var err by remember(a.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(a.id) {
        try { comments = withContext(Dispatchers.IO) { ra.comments(a.id) } } catch (e: Exception) { err = e.message }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconTile(Icons.AutoMirrored.Filled.ArrowBack, onBack); Spacer(Modifier.width(12.dp)); Text("Achievement", fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
        item {
            Column(Modifier.fillMaxWidth().card().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AchBadge(a, 96.dp)
                Spacer(Modifier.height(12.dp))
                Text(a.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(a.desc, color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Points(a); Spacer(Modifier.width(8.dp))
                    Text(listOfNotNull("Missable".takeIf { a.missable }, a.earned?.let { "Unlocked ${it.take(10)}" + if (a.hardcore) " (hardcore)" else "" }, "${a.pct}% of players").joinToString(" · "), color = p.sub, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Pill("Open on RetroAchievements", true) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org/achievement/${a.id}"))) }
            }
        }
        item { Text("Comments" + (comments?.let { " (${it.size})" } ?: ""), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        when {
            err != null -> item { Text(err!!, color = p.sub) }
            comments == null -> item { Text("Loading…", color = p.sub) }
            comments!!.isEmpty() -> item { Text("No comments yet.", color = p.sub) }
            else -> items(comments!!) { c ->
                Column(Modifier.fillMaxWidth().card(18.dp).padding(14.dp)) {
                    Row { Text(c.user, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(c.date, color = p.sub, fontSize = 12.sp) }
                    Text(c.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun Setup(u0: String, k0: String, onSave: (String, String) -> Unit) {
    val p = LocalP.current
    var u by remember { mutableStateOf(u0) }
    var k by remember { mutableStateOf(k0) }
    Column(Modifier.padding(16.dp).fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect RetroAchievements", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Use the same account as Cocoon. Your Web API Key is at retroachievements.org → Settings → Authentication.", color = p.sub, fontSize = 13.sp)
        OutlinedTextField(u, { u = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(k, { k = it.trim() }, label = { Text("Web API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Pill("Save", true) { if (u.isNotEmpty() && k.isNotEmpty()) onSave(u, k) }
    }
}

// ---- Small Cocoon-style building blocks
@Composable
fun Modifier.card(r: Dp = 22.dp): Modifier {
    val p = LocalP.current
    val shape = RoundedCornerShape(r)
    return shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = .3f), spotColor = Color.Black.copy(alpha = .3f))
        .clip(shape).background(Brush.verticalGradient(p.card))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent)), shape)
}

@Composable
fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalP.current
    Text(text, color = if (on) Color.White else p.sub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
        modifier = Modifier.clip(CircleShape).background(if (on) Brush.horizontalGradient(p.accent) else SolidColor(p.tile))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp))
}

@Composable
fun IconTile(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(LocalP.current.tile).clickable(onClick = onClick), Alignment.Center) { Icon(icon, null) }
}

@Composable
fun AchBadge(a: Ach, size: Dp) {
    val p = LocalP.current
    Box {
        AsyncImage("$MEDIA/Badge/${a.badge}${if (a.earned == null) "_lock" else ""}.png", null, Modifier.size(size).clip(RoundedCornerShape(size / 4))
            .then(if (a.hardcore) Modifier.border(2.dp, p.gold, RoundedCornerShape(size / 4)) else Modifier))
        if (a.earned != null) Icon(Icons.Default.Check, null, tint = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(18.dp).clip(CircleShape).background(Brush.horizontalGradient(p.accent)).padding(2.dp))
    }
}

@Composable
fun Points(a: Ach) {
    val p = LocalP.current
    Text("${a.points}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (a.earned != null) Color.White else p.sub,
        modifier = Modifier.clip(CircleShape).background(if (a.earned != null) SolidColor(p.gold) else SolidColor(p.tile)).padding(horizontal = 10.dp, vertical = 4.dp))
}
