package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ArtifactEntity
import com.example.data.local.DevPostEntity
import com.example.data.local.PasswordHasher
import com.example.data.local.PersonalContext
import com.example.domain.prompts.PromptMarqueeGenerator
import com.example.domain.router.CommandRouter
import com.example.domain.router.RouteDecision
import com.example.tts.JarvisTtsManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.EmojiStyle
import com.example.ui.theme.PalettePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches JARVIS`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS", appName)
    }

    @Test
    fun `local math router evaluates expressions with zero tokens`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val router = CommandRouter(context)
        val decision = router.route("24 * 5 + sqrt(144)", AppLanguage.RU)
        assertTrue(decision is RouteDecision.LocalHandled)
        val reply = (decision as RouteDecision.LocalHandled).replyText
        assertTrue(reply.contains("132"))
    }

    @Test
    fun `tts sanitizer strips markdown urls and code blocks`() {
        val raw = "### Привет!\n**Ответ**: проверьте https://example.com и код:\n```kotlin\nval x = 1\n```"
        val clean = JarvisTtsManager.sanitizeForSpeech(raw)
        assertFalse(clean.contains("###"))
        assertFalse(clean.contains("**"))
        assertFalse(clean.contains("https://"))
        assertFalse(clean.contains("val x = 1"))
        assertTrue(clean.contains("Привет!"))
    }

    @Test
    fun `personal context compresses cleanly for system prompt`() {
        val ctx = PersonalContext(music = "Synthwave", movies = "Dune", hobbies = "Android", communicationStyle = "Кратко", exclusions = "Без воды")
        val prompt = ctx.toCompressedPrompt()
        assertTrue(prompt.contains("Music:Synthwave"))
        assertTrue(prompt.contains("Avoid:Без воды"))
    }

    @Test
    fun `password hasher produces salted sha256 and verifies correctly`() {
        val salt1 = PasswordHasher.generateSalt()
        val salt2 = PasswordHasher.generateSalt()
        assertNotEquals(salt1, salt2)
        val hash = PasswordHasher.hashPassword("secret123", salt1)
        assertTrue(PasswordHasher.verify("secret123", salt1, hash))
        assertFalse(PasswordHasher.verify("wrong", salt1, hash))
    }

    @Test
    fun `artifact and devpost entities hold expected metadata`() {
        val note = ArtifactEntity(
            title = "Заметка",
            type = "DOCUMENT",
            category = "All",
            contentOrUri = "- [ ] Сделать сборку",
            tagsCsv = "work,dev",
            isPinned = true
        )
        val post = DevPostEntity(title = "Релиз", body = "Добавлен DevBlog", category = "Добавлено", versionTag = "v1.1")
        assertTrue(note.isPinned)
        assertEquals("work,dev", note.tagsCsv)
        assertEquals("Добавлено", post.category)
    }

    @Test
    fun `fourteen dark palettes and emoji styles are configured`() {
        assertEquals(14, PalettePreset.entries.size)
        assertEquals(4, PalettePreset.entries.count { it.isGradient })
        assertEquals(4, EmojiStyle.entries.size)
    }

    @Test
    fun `prompt marquee generator spans 10000 variants with shuffle and emoji styles`() {
        assertEquals(100, PromptMarqueeGenerator.verbCount())
        assertEquals(100, PromptMarqueeGenerator.topicCount())
        assertEquals(10_000, PromptMarqueeGenerator.TOTAL_COMBINATIONS)

        val p1 = PromptMarqueeGenerator.promptAt(10, 12345L)
        val p2 = PromptMarqueeGenerator.promptAt(11, 12345L)
        val pShuffled = PromptMarqueeGenerator.promptAt(10, 99999L)
        assertNotEquals(p1, p2)
        assertNotEquals(p1, pShuffled)

        val plain = PromptMarqueeGenerator.displayPromptAt(10, 12345L, EmojiStyle.NONE)
        assertEquals(p1, plain)
        val decorated = PromptMarqueeGenerator.displayPromptAt(10, 12345L, EmojiStyle.MINIMAL_KAOMOJI)
        assertTrue(decorated.length > plain.length)
    }
}
