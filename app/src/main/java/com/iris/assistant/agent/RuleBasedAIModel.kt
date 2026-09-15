package com.iris.assistant.agent

import com.iris.assistant.memory.ShortTermMemory

/**
 * A much more flexible, still fully offline, rule-based understanding engine.
 * Used as the automatic fallback whenever the real API-based model
 * (OpenAiIntentModel) is unreachable — no key set, no internet, provider
 * error, etc. — so IRIS still does something useful offline.
 *
 * This is NOT a real language model. What changed compared to the old version:
 *  - Each intent now has many synonyms/phrasings instead of one exact pattern,
 *    and small typos (edit distance 1) are tolerated.
 *  - When something needed is missing (e.g. who to call, what to say), IRIS now
 *    asks a natural follow-up question and remembers the answer on the next
 *    message instead of just failing with "didn't understand".
 *  - A little small talk (greetings/thanks) so it doesn't feel like a dead
 *    command parser.
 *
 * HONEST LIMITATION: this still isn't real language understanding. A sentence
 * that doesn't relate to any known intent, or that mixes several requests in
 * one message, will still fall back to a "didn't understand" reply.
 */
class RuleBasedAIModel : IntentModel {

    // ---------------- multi-turn slot filling ----------------

    private data class PendingSlot(val key: String, val prompt: String)

    private data class PendingIntent(
        val toolName: String,
        val filled: MutableMap<String, String>,
        val remaining: MutableList<PendingSlot>
    )

    private var pending: PendingIntent? = null

    private val cancelWords = listOf("بیخیال", "بی خیال", "ولش کن", "کنسل", "فراموشش کن", "هیچی")

    override suspend fun decide(userText: String, availableTools: Collection<Tool>, memory: ShortTermMemory): Decision {
        val raw = userText.trim()
        if (raw.isEmpty()) return Decision(null, emptyMap(), "بله؟")
        val text = normalize(raw)

        val current = pending
        if (current != null) {
            if (cancelWords.any { text.contains(it) }) {
                pending = null
                return Decision(null, emptyMap(), "باشه، بیخیال شدم.")
            }
            val slot = current.remaining.removeAt(0)
            current.filled[slot.key] = raw
            if (current.remaining.isNotEmpty()) {
                return Decision(null, emptyMap(), current.remaining.first().prompt)
            }
            val toolName = current.toolName
            val params = current.filled.toMap()
            pending = null
            return Decision(toolName, params)
        }

        greetingReply(text)?.let { return Decision(null, emptyMap(), it) }

        val best = intents.map { it to it.score(text) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?: return Decision(null, emptyMap(), fallbacks.random())

        return best.first.build(raw, text)
    }

    // ---------------- intent definitions ----------------

    private inner class Intent(
        val triggers: List<Pair<String, Int>>,
        val build: (raw: String, text: String) -> Decision
    ) {
        fun score(text: String): Int {
            var total = 0
            for ((keyword, weight) in triggers) {
                if (fuzzyContains(text, keyword)) total += weight
            }
            return total
        }
    }

    private fun startPending(toolName: String, filled: MutableMap<String, String>, slots: List<PendingSlot>): Decision {
        pending = PendingIntent(toolName, filled, slots.toMutableList())
        return Decision(null, emptyMap(), slots.first().prompt)
    }

    private val intents: List<Intent> by lazy {
        listOf(
            // Alarm — checked with higher weight on specific words so "زنگ ساعت ۷"
            // isn't mistaken for a phone call.
            Intent(
                listOf("آلارم" to 3, "بیدارم کن" to 3, "بیدار کن" to 3, "ساعت" to 2, "زنگ" to 1)
            ) { _, text ->
                val time = extractTime(text) ?: "07:00"
                Decision("set_alarm", mapOf("time" to time))
            },

            // Flashlight
            Intent(
                listOf("چراغ قوه" to 3, "چراغ‌قوه" to 3, "فلاش" to 2, "چراغ" to 1, "لایت" to 2, "flashlight" to 2)
            ) { _, text ->
                val off = text.contains("خاموش") || text.contains("قطع")
                Decision("control_flashlight", mapOf("state" to if (off) "off" else "on"))
            },

            // SMS
            Intent(
                listOf(
                    "اس ام اس" to 3, "اسمس" to 3, "پیامک" to 3, "پیام بده" to 3,
                    "بنویس به" to 2, "بگو به" to 2, "پیام" to 1
                )
            ) { raw, _ ->
                val (contact, message) = extractSms(raw)
                when {
                    contact != null && message != null ->
                        Decision("send_sms", mapOf("contact" to contact, "message" to message))
                    contact != null ->
                        startPending(
                            "send_sms", mutableMapOf("contact" to contact),
                            listOf(PendingSlot("message", "چی بهش بگم؟"))
                        )
                    else ->
                        startPending(
                            "send_sms", mutableMapOf(),
                            listOf(
                                PendingSlot("contact", "به کی پیام بدم؟"),
                                PendingSlot("message", "چی بهش بگم؟")
                            )
                        )
                }
            },

            // Call
            Intent(
                listOf("تماس بگیر" to 3, "زنگ بزن" to 3, "تماس" to 2, "تلفن کن" to 2, "کال کن" to 2, "زنگ" to 1)
            ) { raw, _ ->
                val name = extractAfter(raw, listOf("زنگ بزن به", "تماس بگیر با", "تماس با", "به", "با"))
                if (!name.isNullOrBlank()) {
                    Decision("make_call", mapOf("contact" to name))
                } else {
                    startPending("make_call", mutableMapOf(), listOf(PendingSlot("contact", "با کی تماس بگیرم؟")))
                }
            },

            // Open app
            Intent(
                listOf("باز کن" to 3, "بازکن" to 3, "اجرا کن" to 2, "برو تو" to 2, "باز شو" to 2)
            ) { raw, _ ->
                val app = extractAfter(raw, listOf("باز کن", "بازکن", "اجرا کن", "برو تو", "باز شو"))
                if (!app.isNullOrBlank()) {
                    Decision("open_app", mapOf("app" to app))
                } else {
                    startPending("open_app", mutableMapOf(), listOf(PendingSlot("app", "کدوم اپ رو باز کنم؟")))
                }
            },

            // Media
            Intent(
                listOf("موسیقی" to 2, "آهنگ" to 2, "پخش کن" to 2, "پخش" to 1, "music" to 2)
            ) { _, text ->
                when {
                    text.contains("بعدی") || text.contains("next") -> Decision("next_track", emptyMap())
                    text.contains("قبلی") || text.contains("previous") -> Decision("previous_track", emptyMap())
                    else -> Decision("media_play", emptyMap())
                }
            },

            // Translate
            Intent(
                listOf("ترجمه کن" to 3, "ترجمه" to 2, "ترنسلیت" to 3, "translate" to 3)
            ) { raw, _ ->
                val toTranslate = raw.substringAfter("ترجمه").substringAfter("کن").trim().ifBlank { raw }
                Decision("translate", mapOf("text" to toTranslate, "target" to "en"))
            },

            // Location
            Intent(
                listOf("موقعیت مکانی" to 3, "موقعیت" to 2, "لوکیشن" to 3, "کجام" to 3, "کجا هستم" to 3)
            ) { _, _ -> Decision("get_location", emptyMap()) },

            // Weather
            Intent(
                listOf("آب و هوا" to 3, "هواشناسی" to 3, "هوا چطوره" to 3, "هوا" to 1, "weather" to 2)
            ) { _, _ -> Decision("get_weather", emptyMap()) },

            // OCR
            Intent(
                listOf("متن رو بخون" to 3, "عکس رو بخون" to 3, "بخون" to 2, "ocr" to 3)
            ) { _, _ -> Decision("ocr", emptyMap()) },

            // Object detection
            Intent(
                listOf("چی می‌بینی" to 3, "چی میبینی" to 3, "این چیه" to 3, "تشخیص" to 2, "اشیاء" to 2)
            ) { _, _ -> Decision("object_detection", emptyMap()) },

            // Accessibility click
            Intent(
                listOf("بزن روی" to 3, "کلیک روی" to 3, "کلیک کن" to 2, "لمس کن" to 2, "کلیک" to 1)
            ) { raw, _ ->
                val target = extractAfter(raw, listOf("بزن روی", "کلیک روی", "روی"))
                if (!target.isNullOrBlank()) {
                    Decision("accessibility_click", mapOf("text" to target))
                } else {
                    startPending("accessibility_click", mutableMapOf(), listOf(PendingSlot("text", "روی چی کلیک کنم؟")))
                }
            },

            // Accessibility scroll
            Intent(
                listOf("اسکرول کن" to 3, "اسکرول" to 2, "بکش پایین" to 2, "بکش بالا" to 2, "برو پایین" to 1, "برو بالا" to 1)
            ) { _, _ -> Decision("accessibility_scroll", emptyMap()) }
        )
    }

    // ---------------- small talk ----------------

    private fun greetingReply(text: String): String? {
        val howAreYou = listOf("خوبی", "چطوری", "حالت چطوره", "حالت خوبه")
        val greetings = listOf("سلام", "درود", "hi", "hello")
        val thanks = listOf("ممنون", "مرسی", "متشکر", "دمت گرم")
        return when {
            howAreYou.any { text.contains(it) } -> "خوبم، ممنون! تو چطوری؟ چه کمکی از دستم برمیاد؟"
            greetings.any { w -> text == w || text.startsWith("$w ") } -> "سلام! چه کمکی از دستم برمیاد؟"
            thanks.any { text.contains(it) } -> "خواهش می‌کنم :)"
            else -> null
        }
    }

    private val fallbacks = listOf(
        "متوجه نشدم. می‌تونی یه‌جور دیگه بگی؟ مثلاً «زنگ بزن به علی» یا «چراغ قوه رو روشن کن»",
        "این یکی رو نفهمیدم. امتحان کن بگو «به مامان بگو دیر می‌رسم» یا «آلارم ساعت ۷»",
        "دقیق نفهمیدم منظورت چیه. می‌تونی مثل این بگی: «اینستاگرام رو باز کن» یا «آهنگ پخش کن»"
    )

    // ---------------- text handling helpers ----------------

    private val digitMap = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private fun normalize(input: String): String {
        // Persian/Arabic-Indic digits (۷, ٧) must become ASCII (7) BEFORE any
        // \d regex runs, otherwise every "ساعت ۷" style time is invisible to
        // extractTime() and silently falls back to a default time.
        var s = input.map { digitMap[it] ?: it }.joinToString("")
        s = s.replace('ي', 'ی').replace('ك', 'ک')
        s = s.replace(Regex("[\u064B-\u0652\u0670]"), "")
        s = s.replace('\u200c', ' ')
        s = s.replace(Regex("[،,.!؟?؛;:\"'()\\[\\]{}]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.lowercase()
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    /** Matches whole phrases as substrings; matches single words with 1-typo tolerance. */
    private fun fuzzyContains(text: String, keyword: String): Boolean {
        val k = normalize(keyword)
        if (k.contains(" ")) return text.contains(k)
        if (text.contains(k)) return true
        if (k.length < 4) return false
        return text.split(" ").any { w ->
            w.length in (k.length - 2)..(k.length + 2) && levenshtein(w, k) <= 1
        }
    }

    private fun cleanWord(w: String): String =
        w.trim().trim('،', ',', '.', '!', '؟', '?', '؛', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}')

    /** Finds the first matching keyword/phrase as whole word(s) and returns whatever follows it. */
    private fun extractAfter(raw: String, keywords: List<String>): String? {
        val words = raw.trim().split(" ").map { cleanWord(it) }.filter { it.isNotBlank() }
        for (kw in keywords.sortedByDescending { it.length }) {
            val kwWords = kw.split(" ")
            if (kwWords.size > words.size) continue
            for (i in 0..(words.size - kwWords.size)) {
                if (words.subList(i, i + kwWords.size) == kwWords) {
                    val rest = words.subList(i + kwWords.size, words.size)
                    val value = rest.firstOrNull { it !in FILLER_WORDS }
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
        return null
    }

    private fun extractSms(raw: String): Pair<String?, String?> {
        val patterns = listOf(
            Regex("""به\s+(\S+)\s+بگو\s+(.+)"""),
            Regex("""به\s+(\S+)\s+بنویس\s+(.+)"""),
            Regex("""پیام\s+(?:بده\s+)?به\s+(\S+)\s+(?:که\s+)?(.+)"""),
            Regex("""(?:اس[\s‌]?ام[\s‌]?اس|پیامک)\s+به\s+(\S+)\s+(.+)""")
        )
        for (p in patterns) {
            val m = p.find(raw)
            if (m != null) return cleanWord(m.groupValues[1]) to m.groupValues[2].trim()
        }
        val contactOnly = listOf(
            Regex("""به\s+(\S+)\s+پیام"""),
            Regex("""پیام\s+(?:بده\s+)?به\s+(\S+)""")
        )
        for (p in contactOnly) {
            val m = p.find(raw)
            if (m != null) return cleanWord(m.groupValues[1]) to null
        }
        return null to null
    }

    private fun extractTime(text: String): String? {
        val hm = Regex("""(\d{1,2})[:.](\d{2})""").find(text)
        if (hm != null) return "${hm.groupValues[1].padStart(2, '0')}:${hm.groupValues[2]}"
        val hourOnly = Regex("""ساعت\s*(\d{1,2})""").find(text)
        if (hourOnly != null) return "${hourOnly.groupValues[1].padStart(2, '0')}:00"
        return null
    }

    companion object {
        private val FILLER_WORDS = listOf(
            "رو", "را", "به", "با", "کن", "بزن", "بگو", "لطفا", "لطفاً", "برام", "میشه", "می‌شه", "واسم"
        )
    }
}
