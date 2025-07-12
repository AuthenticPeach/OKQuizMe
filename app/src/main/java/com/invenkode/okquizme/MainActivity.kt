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
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView



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
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerDecks: Spinner




    private var flashcards = listOf<Flashcard>()
    private var currentIndex = 0
    private var showingFront = true
    private val knownCards = mutableSetOf<Int>()
    private var hasAdvanced = false


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
        progressBar           = findViewById(R.id.progressBar)
        spinnerDecks          = findViewById(R.id.spinnerDecks)


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
            hasAdvanced = false
            updateFlashcard()
        }

        btnStudyRemaining.setOnClickListener {
            flashcards = flashcards
                .filterIndexed { i, _ -> i !in knownCards }
                .shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            hasAdvanced = false
            updateFlashcard()
        }

        btnReverseQuiz.setOnClickListener {
            isReversedMode = true
            flashcards = flashcards.shuffled()
            knownCards.clear()
            currentIndex = 0
            showingFront = true
            hasAdvanced = false
            updateFlashcard()
        }

        val deckName = sharedPrefs.getString("current_deck", null)
        deckName?.let {
            sharedPrefs.getString("cards_json_$it", null)?.let { json ->
                flashcards = Gson().fromJson(json, Array<Flashcard>::class.java).toList()
            }
        }

        spinnerDecks.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val deckName = parent?.getItemAtPosition(position) as? String ?: return
                val deckJson = sharedPrefs.getString("cards_json_$deckName", null) ?: return

                flashcards = Gson().fromJson(deckJson, Array<Flashcard>::class.java).toList().shuffled()
                sharedPrefs.edit().putString("current_deck", deckName).apply()
                knownCards.clear()
                currentIndex = 0
                showingFront = true
                isReversedMode = false
                updateFlashcard()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.data?.let { uri ->
            val deckName = uri.lastPathSegment?.substringAfterLast("/") ?: "deck"
            sharedPrefs.edit().putString("current_deck", deckName).apply()
            sharedPrefs.edit().putString("cards_json_$deckName", Gson().toJson(flashcards)).apply()

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

        // 1) If there are still cards _ahead_, advance and show it:
        if (currentIndex < flashcards.size - 1) {
            currentIndex++
            showingFront = true
            updateFlashcard()
            return
        }

        // 2) Otherwise, you were on the final card and tapped Next → show complete UI:
        val unknownLeft = flashcards.size - knownCards.size
        tvCardTitle.text   = "Deck Complete"
        tvFlashcard.text   = "🔔 Deck Complete!\nUnknown left: $unknownLeft"
        updateCounts()
        showEndButtons()
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
            tvCardTitle.text = ""
            tvFlashcard.text = "No cards loaded."
            showNormalButtons()
            return
        }

        // Always show the card text:
        val card     = flashcards[currentIndex]
        val front    = if (isReversedMode) card.back else card.front
        val back     = if (isReversedMode) card.front else card.back
        val newText  = if (showingFront) front else back

        animateFlashcardFlip(newText)
        tvCardTitle.text = "Card ${currentIndex + 1} / ${flashcards.size}"
        showNormalButtons()
        tts.speak(newText, TextToSpeech.QUEUE_FLUSH, null, null)

        if (flashcards.isNotEmpty() && !isEndOfDeck()) {
            val progress = ((currentIndex + 1).toFloat() / flashcards.size * 100).toInt()
            progressBar.progress = progress
        } else {
            progressBar.progress = 100
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
        spinnerDecks.visibility         = View.GONE

    }

    private fun showEndButtons() {
        btnFlip.visibility               = View.GONE
        btnNext.visibility               = View.GONE
        btnKnown.visibility              = View.GONE
        btnStudyAgainShuffle.visibility  = View.VISIBLE
        btnStudyRemaining.visibility     = View.VISIBLE

        btnReverseQuiz.visibility       = View.VISIBLE
        val decks = loadDeckList()
        if (decks.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, decks)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDecks.adapter = adapter
            spinnerDecks.visibility = View.VISIBLE
        }

    }
    private fun loadDeckList(): List<String> {
        val allPrefs = sharedPrefs.all
        return allPrefs.keys
            .filter { it.startsWith("cards_json_") }
            .map { it.removePrefix("cards_json_") }
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

