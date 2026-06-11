package com.yiqun.translator.ui.screen.intro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yiqun.translator.R
import com.yiqun.translator.data.local.preference.PreferenceRepository
import com.yiqun.translator.ui.theme.SenseGroupTranslatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * First-run practice playground, shown once right after the floating handle
 * first appears. The user performs the REAL gesture (drag the pointer onto a
 * word of the bundled sample text) against this screen — real capture, real
 * OCR, real translation — before ever needing it inside a third-party app.
 * Interactive try-it beats any static tutorial; no commercial screen
 * translator does this, which makes it a differentiator.
 *
 * The sample text is intentionally rendered large and high-contrast so OCR
 * succeeds on the first attempt — first-attempt success is the tutorial.
 */
@AndroidEntryPoint
class PracticeActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PracticeActivity::class.java)
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

        // Mark as shown immediately: whatever way the user leaves (button,
        // back gesture, home), the playground never reappears.
        preferenceRepository.update(PreferenceRepository.WAS_PRACTICE_SHOWN, true)

        setContent {
            SenseGroupTranslatorTheme {
                val backgroundColor = Color(0xFFFAF8F4)

                SideEffect {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = true
                    }
                    window.statusBarColor = backgroundColor.toArgb()
                    window.navigationBarColor = backgroundColor.toArgb()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    PracticeScreen(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun PracticeScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color(0xFF2962FF), shape = RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_detect_mode_sense_group),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.practice_instruction),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sample text: foreign-language relative to the UI locale, large and
        // dark-on-light for reliable OCR.
        Text(
            text = stringResource(R.string.practice_sample_1),
            color = Color(0xFF1E1E1E),
            fontSize = 20.sp,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.practice_sample_2),
            color = Color(0xFF1E1E1E),
            fontSize = 20.sp,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF))
        ) {
            Text(
                text = stringResource(R.string.practice_done),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
