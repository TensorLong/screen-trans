package com.yiqun.translator.data.remote.ai.chatgpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SenseGroupChunkPolicyTest {

    @Test
    fun systemPromptStaysUnderTwoHundredWords() {
        val wordCount = SenseGroupChunkPolicy.SYSTEM_PROMPT
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size

        assertTrue("system prompt has $wordCount words", wordCount <= 200)
    }

    @Test
    fun systemPromptUsesSourceAndTargetChunkContract() {
        val prompt = SenseGroupChunkPolicy.SYSTEM_PROMPT
        // v3.3.7 prompt is model-agnostic: validated via OpenRouter across 14 non-reasoning
        // models. Removed the GPT-only "first translate the whole sentence" instruction
        // that caused Claude / Gemini / DeepSeek / Qwen / Kimi to emit whole-sentence
        // translations. Keep these key contract markers stable.
        assertTrue("must declare JSON-only output", prompt.contains("ONLY a JSON object"))
        assertTrue("must forbid markdown fences", prompt.contains("No markdown"))
        assertTrue("must forbid code fences", prompt.contains("No code fences"))
        assertTrue("must require source_chunk be a substring", prompt.contains("source_chunk MUST be an exact substring"))
        assertTrue("must forbid whole-sentence chunk", prompt.contains("Do NOT return the whole sentence"))
        assertTrue("must require target_chunk translate only the chunk", prompt.contains("target_chunk MUST translate ONLY source_chunk"))
        assertTrue("must specify output schema", prompt.contains("{\"source_chunk\":\"...\",\"target_chunk\":\"...\"}"))
        assertTrue("must NOT contain the GPT-only whole-sentence instruction", !prompt.contains("First translate the whole sentence"))
    }

    @Test
    fun refineChunkShrinksWholeSentenceVerbAnswer() {
        val sentence = "The committee made a careful decision after reviewing the evidence."
        val offset = sentence.indexOf("made")

        val chunk = SenseGroupChunkPolicy.refineChunk(
            sentence = sentence,
            modelChunk = sentence,
            word = "made",
            pointedTokenOffset = offset,
        )

        assertEquals("made a careful decision", chunk)
    }

    @Test
    fun refineChunkShrinksWholeSentenceNounAnswer() {
        val sentence = "The committee made a careful decision after reviewing the evidence."
        val offset = sentence.indexOf("decision")

        val chunk = SenseGroupChunkPolicy.refineChunk(
            sentence = sentence,
            modelChunk = sentence,
            word = "decision",
            pointedTokenOffset = offset,
        )

        assertEquals("a careful decision", chunk)
    }

    @Test
    fun refineChunkKeepsTightModelAnswer() {
        val sentence = "The committee made a careful decision after reviewing the evidence."
        val offset = sentence.indexOf("reviewing")

        val chunk = SenseGroupChunkPolicy.refineChunk(
            sentence = sentence,
            modelChunk = "reviewing the evidence",
            word = "reviewing",
            pointedTokenOffset = offset,
        )

        assertEquals("reviewing the evidence", chunk)
    }

    @Test
    fun refineChunkKeepsTightAdjectiveNounPhrase() {
        val sentence = "They will make careful choice."
        val offset = sentence.indexOf("careful")

        val chunk = SenseGroupChunkPolicy.refineChunk(
            sentence = sentence,
            modelChunk = "careful choice",
            word = "careful",
            pointedTokenOffset = offset,
        )

        assertEquals("careful choice", chunk)
    }

    @Test
    fun refineChunkShrinksShortButOverExpandedVerbAnswer() {
        val sentence = "The engineer reviewed the patch before release."
        val offset = sentence.indexOf("reviewed")

        val chunk = SenseGroupChunkPolicy.refineChunk(
            sentence = sentence,
            modelChunk = "reviewed the patch before release",
            word = "reviewed",
            pointedTokenOffset = offset,
        )

        assertEquals("reviewed the patch", chunk)
    }

    @Test
    fun refineChunkShrinksTwentyWholeSentenceVerbAnswers() {
        val cases = listOf(
            VerbCase("The committee made a careful decision after reviewing the evidence.", "made", "made a careful decision"),
            VerbCase("The engineer reviewed the patch before release.", "reviewed", "reviewed the patch"),
            VerbCase("She keeps the selected word visible while dragging.", "keeps", "keeps the selected word visible"),
            VerbCase("The app returns the smallest phrase when asked.", "returns", "returns the smallest phrase"),
            VerbCase("They will translate the highlighted phrase tomorrow.", "translate", "will translate the highlighted phrase"),
            VerbCase("He can open the menu quickly.", "open", "can open the menu"),
            VerbCase("The service starts recognition after a delay.", "starts", "starts recognition"),
            VerbCase("A spinner appears beside the pointer.", "appears", "appears"),
            VerbCase("The test checks sentence mode with uneven spacing.", "checks", "checks sentence mode"),
            VerbCase("Users select fixed area from the menu.", "select", "select fixed area"),
            VerbCase("The overlay shows a check mark next to the active mode.", "shows", "shows a check mark"),
            VerbCase("It did not split the sentence despite wide gaps.", "split", "did not split the sentence"),
            VerbCase("The pointer moves across the screen smoothly.", "moves", "moves across the screen"),
            VerbCase("The cache saves repeated requests automatically.", "saves", "saves repeated requests"),
            VerbCase("The model gave the whole sentence again.", "gave", "gave the whole sentence"),
            VerbCase("The policy trims the result locally.", "trims", "trims the result"),
            VerbCase("The app should keep resource usage stable.", "keep", "should keep resource usage stable"),
            VerbCase("The menu marks the selected mode clearly.", "marks", "marks the selected mode"),
            VerbCase("OCR joins words with irregular spaces.", "joins", "joins words"),
            VerbCase("The release includes the debug APK.", "includes", "includes the debug APK"),
        )

        cases.forEach { case ->
            val chunk = SenseGroupChunkPolicy.refineChunk(
                sentence = case.sentence,
                modelChunk = case.sentence,
                word = case.word,
                pointedTokenOffset = case.sentence.indexOf(case.word),
            )

            assertEquals(case.expected, chunk)
        }
    }

    private data class VerbCase(
        val sentence: String,
        val word: String,
        val expected: String,
    )
}
