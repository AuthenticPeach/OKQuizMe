package com.invenkode.okquizme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var tvKnownCount: TextView
    private lateinit var tvUnknownCount: TextView
    private lateinit var tvCardTitle: TextView
    private lateinit var tvFlashcard: TextView
    private lateinit var btnDarkMode: Button
    private lateinit var btnUpload: Button
    private lateinit var btnFlip: Button
    private lateinit var btnNext: Button
    private lateinit var btnKnown: Button
    private lateinit var btnStudyAgainShuffle: Button
    private lateinit var btnStudyRemaining: Button
    private lateinit var btnReverseQuiz: Button
    private var isReversedMode = false


    private var flashcards = listOf<Flashcard>()
    private var currentIndex = 0
    private var showingFront = true
    private val knownCards = mutableSetOf<Int>()

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind root and prefs
        rootLayout = findViewById(R.id.rootLayout)
        sharedPrefs = getSharedPreferences("settings", MODE_PRIVATE)

        // Bind views
        tvKnownCount          = findViewById(R.id.tvKnownCount)
        tvUnknownCount        = findViewById(R.id.tvUnknownCount)
        tvCardTitle           = findViewById(R.id.tvCardTitle)
        tvFlashcard           = findViewById(R.id.tvFlashcard)
        btnDarkMode           = findViewById(R.id.btnDarkMode)
        btnUpload             = findViewById(R.id.btnUpload)
        btnFlip               = findViewById(R.id.btnFlip)
        btnNext               = findViewById(R.id.btnNext)
        btnKnown              = findViewById(R.id.btnKnown)
        btnStudyAgainShuffle  = findViewById(R.id.btnStudyAgainShuffle)
        btnStudyRemaining     = findViewById(R.id.btnStudyRemaining)
        btnReverseQuiz        = findViewById(R.id.btnReverseQuiz)

        // Text-to-speech init
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        // Load saved deck if exists
        getSharedPreferences("flashcards", MODE_PRIVATE)
            .getString("cards_json", null)
            ?.let { json ->
                flashcards = Gson().fromJson(json, Array<Flashcard>::class.java).toList()
            }

        // Apply theme colors & initial UI
        applyThemeColors()
        updateFlashcard()

        // Click listeners
        btnDarkMode.setOnClickListener { toggleDarkMode() }

        btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "text/plain"
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
        }

        tvFlashcard.setOnClickListener {
            // only flip if we have cards and we're not on the end screen
            if (flashcards.isNotEmpty() && !isEndOfDeck()) {
                showingFront = !showingFront
                updateFlashcard()
            }
        }

        btnFlip.setOnClickListener {
            if (flashcards.isNotEmpty() && !isEndOfDeck()) {
                showingFront = !showingFront
                updateFlashcard()
            }
        }

        btnNext.setOnClickListener { goToNextCard() }

        btnKnown.setOnClickListener {
            knownCards.add(currentIndex)
            goToNextCard()
        }

        btnStudyAgainShuffle.setOnClickListener {
            flashcards = flashcards.shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            updateFlashcard()
        }

        btnStudyRemaining.setOnClickListener {
            flashcards = flashcards
                .filterIndexed { i, _ -> i !in knownCards }
                .shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            updateFlashcard()
        }

        btnReverseQuiz.setOnClickListener {
            isReversedMode = true
            flashcards = flashcards.shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            updateFlashcard()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader().use { it?.readText() ?: "" }
                flashcards = parseFlashcards(text).shuffled()
                getSharedPreferences("flashcards", MODE_PRIVATE)
                    .edit()
                    .putString("cards_json", Gson().toJson(flashcards))
                    .apply()
                knownCards.clear()
                currentIndex = 0
                showingFront = true
                updateFlashcard()
            }
        }
    }

    private fun parseFlashcards(text: String): List<Flashcard> {
        val regex = Regex("\"(.*?)\"\\s*[-=]\\s*\"(.*?)\"")
        return text.lines().mapNotNull { line ->
            regex.find(line)?.destructured?.let { (front, back) ->
                Flashcard(front, back)
            }
        }
    }

    private fun goToNextCard() {
        if (flashcards.isEmpty()) return
        if (isEndOfDeck()) {
            // already at end, no-op
            return
        }
        var attempts = 0
        do {
            currentIndex = (currentIndex + 1) % flashcards.size
            attempts++
        } while (
            currentIndex in knownCards &&
            knownCards.size < flashcards.size &&
            attempts < flashcards.size
        )
        showingFront = true
        updateFlashcard()
    }

    private fun animateFlashcardFlip(newText: String) {
        val scale = resources.displayMetrics.density
        tvFlashcard.cameraDistance = 8000 * scale

        val animatorOut = ObjectAnimator.ofFloat(tvFlashcard, "rotationY", 0f, 90f)
        val animatorIn  = ObjectAnimator.ofFloat(tvFlashcard, "rotationY", -90f, 0f)
        animatorOut.duration = 150
        animatorIn.duration  = 150

        animatorOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tvFlashcard.text = newText
                animatorIn.start()
            }
        })
        animatorOut.start()
    }

    private fun updateFlashcard() {
        updateCounts()

        if (flashcards.isEmpty()) {
            tvFlashcard.text = "No cards loaded."
            showNormalButtons()
            return
        }

        if (isEndOfDeck()) {
            val unknownCount = flashcards.size - knownCards.size
            tvCardTitle.text = "Deck Complete"
            tvFlashcard.text = "🔔 Deck Complete!\nUnknown left: $unknownCount"
            showEndButtons()
        } else {
            // pick the raw card
            val card = flashcards[currentIndex]
            // depending on reversed flag, define the “front” and “back”
            val frontText = if (isReversedMode) card.back else card.front
            val backText  = if (isReversedMode) card.front else card.back

            // choose which side to show
            val newText = if (showingFront) frontText else backText

            animateFlashcardFlip(newText)
            tvCardTitle.text = "Card ${currentIndex + 1} / ${flashcards.size}"
            showNormalButtons()
            tts.speak(newText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }


    private fun updateCounts() {
        val known   = knownCards.size
        val unknown = flashcards.size - known
        tvKnownCount.text   = "Known: $known"
        tvUnknownCount.text = "Unknown: $unknown"
    }

    private fun isEndOfDeck(): Boolean =
        currentIndex == flashcards.lastIndex

    private fun showNormalButtons() {
        btnFlip.visibility               = View.VISIBLE
        btnNext.visibility               = View.VISIBLE
        btnKnown.visibility              = View.VISIBLE
        btnStudyAgainShuffle.visibility  = View.GONE
        btnStudyRemaining.visibility     = View.GONE
        btnReverseQuiz.visibility       = View.GONE
    }

    private fun showEndButtons() {
        btnFlip.visibility               = View.GONE
        btnNext.visibility               = View.GONE
        btnKnown.visibility              = View.GONE
        btnStudyAgainShuffle.visibility  = View.VISIBLE
        btnStudyRemaining.visibility     = View.VISIBLE

        btnReverseQuiz.visibility       = View.VISIBLE
    }

    private fun applyThemeColors() {
        val isDark   = sharedPrefs.getBoolean("dark_mode", false)
        val bgColor  = if (isDark) Color.BLACK else Color.WHITE
        val fgColor  = if (isDark) Color.WHITE else Color.BLACK
        val btnTint  = if (isDark) Color.DKGRAY else Color.LTGRAY

        rootLayout.setBackgroundColor(bgColor)
        tvFlashcard.setTextColor(fgColor)
        tvCardTitle.setTextColor(fgColor)

        listOf(
            btnDarkMode,
            btnUpload,
            btnFlip,
            btnNext,
            btnKnown,
            btnStudyAgainShuffle,
            btnStudyRemaining
        ).forEach { btn ->
            btn.setTextColor(fgColor)
            btn.backgroundTintList = ColorStateList.valueOf(btnTint)
        }
    }

    private fun toggleDarkMode() {
        val isDark = sharedPrefs.getBoolean("dark_mode", false)
        sharedPrefs.edit()
            .putBoolean("dark_mode", !isDark)
            .apply()
        applyThemeColors()
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1
    }
}

