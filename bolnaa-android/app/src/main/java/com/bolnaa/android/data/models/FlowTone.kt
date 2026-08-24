package com.bolnaa.android.data.models

enum class FlowTone(
    val title: String,
    val description: String,
    val systemPrompt: String
) {
    NATURAL(
        title = "Natural Smart Flow",
        description = "Removes filler words (um, uh, like), corrects stutters, adds natural punctuation and capitalization.",
        systemPrompt = "You are Bolnaa Voice AI. Clean up the following speech transcript into natural, polished, well-punctuated English. Remove all filler words (e.g. 'um', 'uh', 'you know', 'like' when used as a filler, 'so basically', repeated stutter words). Fix grammar and capitalization while strictly preserving the speaker's original meaning and vocabulary. Output ONLY the cleaned text with no introductory or meta comments."
    ),
    VERBATIM(
        title = "Direct Dictation (Verbatim)",
        description = "Exact word-for-word transcript with basic punctuation.",
        systemPrompt = "You are a transcription formatter. Format the speech transcript with correct punctuation and capitalization, but do NOT remove any words (keep verbatim). Output ONLY the formatted text."
    ),
    PROFESSIONAL(
        title = "Professional & Polished",
        description = "Formats the dictation into structured, professional prose ideal for work emails and Slack messages.",
        systemPrompt = "You are a professional executive writing assistant. Rewrite the spoken dictation into clear, concise, professional business prose suitable for emails or work communication. Remove colloquialisms and filler words. Output ONLY the rewritten text."
    ),
    BULLETS(
        title = "Bullet Points & Notes",
        description = "Summarizes or structures spoken thoughts into neat bulleted points.",
        systemPrompt = "You are a note-taking assistant. Convert the spoken stream of thoughts into a clear, concise bullet-point list. Use markdown bullets (-). Output ONLY the bullet list."
    )
}
