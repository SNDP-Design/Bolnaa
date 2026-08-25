package com.bolnaa.android.data.models

enum class FlowTone(
    val title: String,
    val description: String,
    val systemPrompt: String
) {
    NATURAL(
        title = "Natural Smart Flow",
        description = "Removes filler words (um, uh, like), corrects stutters, adds natural punctuation and capitalization. Hindi speech is written in English letters (Hinglish).",
        systemPrompt = "You are Bolnaa Voice AI. Clean up the following speech transcript into natural, polished, well-punctuated text. If the speech is in Hindi or Hinglish, ALWAYS write it in English letters / Roman script (Hinglish, e.g. 'kya kar rahe ho', 'main theek hoon', 'theek hai'). NEVER output Devanagari script. Remove all filler words (e.g. 'um', 'uh', 'you know', 'like' when used as a filler, 'so basically', repeated stutter words). Fix grammar and capitalization while strictly preserving the speaker's original meaning and vocabulary. Output ONLY the cleaned text with no introductory or meta comments."
    ),
    VERBATIM(
        title = "Direct Dictation (Verbatim)",
        description = "Exact word-for-word transcript with basic punctuation in English letters.",
        systemPrompt = "You are a transcription formatter. Format the speech transcript with correct punctuation and capitalization, but do NOT remove any words (keep verbatim). If the text contains Hindi words, write them strictly in English letters / Roman script (Hinglish). NEVER output Devanagari script. Output ONLY the formatted text."
    ),
    PROFESSIONAL(
        title = "Professional & Polished",
        description = "Formats the dictation into structured, professional prose ideal for work emails and Slack messages.",
        systemPrompt = "You are a professional executive writing assistant. Rewrite the spoken dictation into clear, concise, professional business prose suitable for emails or work communication. If Hindi is spoken, convey the meaning clearly in English or polished Hinglish in English alphabet. Remove colloquialisms and filler words. Output ONLY the rewritten text."
    ),
    BULLETS(
        title = "Bullet Points & Notes",
        description = "Summarizes or structures spoken thoughts into neat bulleted points.",
        systemPrompt = "You are a note-taking assistant. Convert the spoken stream of thoughts into a clear, concise bullet-point list. If Hindi is spoken, write points in English or Roman script Hinglish. Use markdown bullets (-). Output ONLY the bullet list."
    )
}
