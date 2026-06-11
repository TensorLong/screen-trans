package com.yiqun.translator.ui.screen.intro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yiqun.translator.R
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.ui.theme.SenseGroupTranslatorTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * First-run hero screen: one page that demos the sense-group gesture
 * (pointer slides under a word, the sense group highlights, a translation
 * bubble pops) instead of the old six-page video carousel. Research across
 * top open-source apps showed multi-page feature carousels are skipped;
 * a single looping demo of the hero moment plus one CTA performs best.
 */
@AndroidEntryPoint
class WelcomeActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WelcomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // SplashActivity waits underneath for WAS_TRAILER_SHOWN; backing out
        // here must close the whole task or it would reveal a transparent,
        // content-less splash window.
        onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
        }

        setContent {
            SenseGroupTranslatorTheme {
                val backgroundColor = Color(0xFF1A1B20)

                SideEffect {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                    window.statusBarColor = backgroundColor.toArgb()
                    window.navigationBarColor = backgroundColor.toArgb()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    WelcomeScreen(
                        onStart = {
                            // SplashActivity stays alive underneath and resumes
                            // the permission flow once this flag flips true.
                            preferenceRepository.update(PreferenceRepository.WAS_TRAILER_SHOWN, true)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

private val AccentColor = Color(0xFF4FC3F7)
private val HighlightColor = Color(0xFF2962FF)

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            painter = painterResource(R.drawable.ic_detect_mode_sense_group),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = AccentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_tagline),
            color = Color(0xFFB8BCC6),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(28.dp))

        SenseGroupDemoCard()

        Spacer(modifier = Modifier.height(28.dp))

        WelcomeStep(number = "1", text = stringResource(R.string.welcome_step_1))
        Spacer(modifier = Modifier.height(10.dp))
        WelcomeStep(number = "2", text = stringResource(R.string.welcome_step_2))
        Spacer(modifier = Modifier.height(10.dp))
        WelcomeStep(number = "3", text = stringResource(R.string.welcome_step_3))

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HighlightColor)
        ) {
            Text(
                text = stringResource(R.string.welcome_start),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WelcomeStep(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color = Color(0xFF2A2D36), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, color = Color(0xFFE3E5EA), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Looping animation of the hero gesture. Timeline per loop:
 * pointer glides under the sense-group chunk → chunk background lights up →
 * translation bubble fades in above the sentence → hold → everything resets.
 * The chunk rectangle comes from the real text layout, so the pointer lands
 * under the actual words in every locale and font scale.
 */
@Composable
private fun SenseGroupDemoCard() {
    val prefix = stringResource(R.string.welcome_demo_prefix)
    val chunk = stringResource(R.string.welcome_demo_chunk)
    val suffix = stringResource(R.string.welcome_demo_suffix)
    val translation = stringResource(R.string.welcome_demo_translation)

    val pointerProgress = remember { Animatable(0f) }
    val highlightAlpha = remember { Animatable(0f) }
    val bubbleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            pointerProgress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
            highlightAlpha.animateTo(1f, tween(durationMillis = 350))
            bubbleAlpha.animateTo(1f, tween(durationMillis = 350))
            delay(2400)
            bubbleAlpha.animateTo(0f, tween(durationMillis = 300))
            highlightAlpha.animateTo(0f, tween(durationMillis = 300))
            pointerProgress.animateTo(0f, tween(durationMillis = 500, easing = FastOutSlowInEasing))
            delay(600)
        }
    }

    var chunkRect by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xFF23252D), shape = RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Translation bubble slot — fixed height so the card never jumps.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .alpha(bubbleAlpha.value)
                        .background(color = HighlightColor, shape = RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = translation,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val annotated = buildAnnotatedString {
                append(prefix)
                withStyle(
                    SpanStyle(
                        background = HighlightColor.copy(alpha = 0.45f * highlightAlpha.value),
                        color = if (highlightAlpha.value > 0.5f) Color.White else Color(0xFFD2D5DC)
                    )
                ) {
                    append(chunk)
                }
                append(suffix)
            }
            Text(
                text = annotated,
                color = Color(0xFFD2D5DC),
                fontSize = 17.sp,
                lineHeight = 26.sp,
                onTextLayout = { layoutResult ->
                    val start = prefix.length
                    val end = prefix.length + chunk.length - 1
                    if (end >= start) {
                        val first = layoutResult.getBoundingBox(start)
                        val last = layoutResult.getBoundingBox(end)
                        chunkRect = Rect(
                            left = minOf(first.left, last.left),
                            top = minOf(first.top, last.top),
                            right = maxOf(first.right, last.right),
                            bottom = maxOf(first.bottom, last.bottom),
                        )
                    }
                }
            )

            // Pointer travel lane below the sentence.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val rect = chunkRect
                if (rect != null) {
                    val pointerSize = 26.dp
                    val pointerSizePx = with(density) { pointerSize.toPx() }
                    val laneWidthPx = with(density) { 44.dp.toPx() } // fallback start
                    val startX = 0f
                    val targetX = rect.left + (rect.right - rect.left) / 2f - pointerSizePx / 2f
                    val x = startX + (targetX - startX) * pointerProgress.value
                    val y = (1f - pointerProgress.value) * laneWidthPx * 0.3f
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .size(pointerSize)
                            .background(color = AccentColor.copy(alpha = 0.25f), shape = CircleShape)
                            .padding(5.dp)
                            .background(color = AccentColor, shape = CircleShape)
                    )
                }
            }
        }
    }
}
