package com.elizabethwhitebaker.egidtracker

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.hypot


class MainActivity : AppCompatActivity() {
        private lateinit var auth: FirebaseAuth

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_landing)

            // The first frame matches the splash exactly (see playIntro), so drop the splash
            // at once instead of the system's fade-out, which briefly washes out the logo.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                splashScreen.setOnExitAnimationListener { it.remove() }
            }

            // Draw the background edge-to-edge (behind the status/nav bars)
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val landingRoot = findViewById<View>(R.id.landingRoot)
            ViewCompat.setOnApplyWindowInsetsListener(landingRoot) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            auth = FirebaseAuth.getInstance()

            val signUp = findViewById<Button>(R.id.signUpButton)
            val signIn = findViewById<Button>(R.id.signInButton)

            // Set click listeners for Register and Login buttons
            signUp.setOnClickListener {
                // Navigate to the Sign Up activity
                startActivity(Intent(this, SignUpActivity::class.java))
            }

            signIn.setOnClickListener {
                // Navigate to the Log In activity
                startActivity(Intent(this, SignInActivity::class.java))
            }

            // Only on a fresh launch, not when recreated (e.g. rotation)
            if (savedInstanceState == null) playIntro(landingRoot)
        }

        /**
         * Launch intro that continues the system splash (drawable/splash_logo.xml: the logo at
         * landing size on a dark radial glow; Android 12+ clips splash icons to a 192dp circle,
         * too small to fit the title too). The first frame matches that splash exactly, then:
         *  1. the glow spreads across the whole screen while the title fades in under the logo;
         *  2. logo and title slide up into place, the glow fades out to the plain landing
         *     background, and the rest of the page fades in.
         */
        private fun playIntro(root: View) {
            val logo = findViewById<View>(R.id.landingImage)
            val title = findViewById<View>(R.id.navTitle)
            val rest = listOf<View>(
                findViewById(R.id.introBox),
                findViewById(R.id.signUpButton),
                findViewById(R.id.signInButton)
            )
            title.alpha = 0f
            rest.forEach { it.alpha = 0f }

            val density = resources.displayMetrics.density
            val glow = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(
                    ContextCompat.getColor(this@MainActivity, R.color.splashCenter),
                    ContextCompat.getColor(this@MainActivity, R.color.primaryBackground)
                )
                gradientRadius = SPLASH_GLOW_RADIUS_DP * density
            }
            val landingBackground = root.background
            root.background = LayerDrawable(arrayOf(
                ColorDrawable(ContextCompat.getColor(this, R.color.primaryBackground)), glow
            ))

            // Positions are only known after layout, which still runs before the first frame
            root.doOnLayout {
                // Start with the logo centered on screen, where the splash drew it
                val offset = root.height / 2f - (logo.top + logo.bottom) / 2f
                listOf(logo, title).forEach { view ->
                    view.translationY = offset
                    view.animate()
                        .translationY(0f)
                        .setStartDelay(INTRO_SLIDE_DELAY_MS)
                        .setDuration(INTRO_SLIDE_MS)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()
                }

                title.animate()
                    .alpha(1f)
                    .setStartDelay(INTRO_START_DELAY_MS + INTRO_TITLE_DELAY_MS)
                    .setDuration(INTRO_SPREAD_MS)
                    .start()

                // Spread the glow from the splash circle to the screen corners
                val fullRadius = hypot(root.width / 2f, root.height / 2f)
                ValueAnimator.ofFloat(glow.gradientRadius, fullRadius).apply {
                    startDelay = INTRO_START_DELAY_MS
                    duration = INTRO_SPREAD_MS
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { glow.gradientRadius = it.animatedValue as Float }
                    start()
                }
                // Then fade it out, leaving the plain landing background
                ValueAnimator.ofInt(255, 0).apply {
                    startDelay = INTRO_SLIDE_DELAY_MS
                    duration = INTRO_SLIDE_MS
                    addUpdateListener { glow.alpha = it.animatedValue as Int }
                    doOnEnd { root.background = landingBackground }
                    start()
                }

                rest.forEach { view ->
                    view.animate()
                        .alpha(1f)
                        .setStartDelay(INTRO_SLIDE_DELAY_MS + INTRO_SLIDE_MS / 2)
                        .setDuration(INTRO_FADE_MS)
                        .start()
                }
            }
        }

        companion object {
            // Must match the glow radius in drawable/splash_logo.xml
            private const val SPLASH_GLOW_RADIUS_DP = 96f

            // Hold the splash picture for the first frames, while the system splash is removed
            private const val INTRO_START_DELAY_MS = 150L
            private const val INTRO_SPREAD_MS = 500L
            private const val INTRO_TITLE_DELAY_MS = 150L
            private const val INTRO_SLIDE_DELAY_MS = INTRO_START_DELAY_MS + 800L
            private const val INTRO_SLIDE_MS = 600L
            private const val INTRO_FADE_MS = 350L
        }
    }

