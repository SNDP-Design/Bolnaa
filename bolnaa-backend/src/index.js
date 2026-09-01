const MAX_AUDIO_BYTES = 25 * 1024 * 1024;
const GROQ_TRANSCRIPTION_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

function json(body, status = 200, origin = "*") {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json",
      "access-control-allow-origin": origin,
      "access-control-allow-headers": "content-type, authorization",
      "access-control-allow-methods": "POST, OPTIONS",
      "cache-control": "no-store"
    }
  });
}

export default {
  async fetch(request, env) {
    const origin = env.ALLOWED_ORIGIN || "*";

    if (request.method === "OPTIONS") {
      return json({ ok: true }, 200, origin);
    }

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "bolnaa-api" }, 200, origin);
    }

    if (request.method !== "POST" || url.pathname !== "/transcribe") {
      return json({ error: "Not found" }, 404, origin);
    }

    if (!env.GROQ_API_KEY) {
      return json({ error: "Groq is not configured on the server" }, 503, origin);
    }

    const contentLength = Number(request.headers.get("content-length") || 0);
    if (contentLength > MAX_AUDIO_BYTES) {
      return json({ error: "Audio file is too large" }, 413, origin);
    }

    let form;
    try {
      form = await request.formData();
    } catch {
      return json({ error: "Expected multipart audio upload" }, 400, origin);
    }

    const audio = form.get("file");
    if (!(audio instanceof File) || audio.size === 0) {
      return json({ error: "Audio file is required" }, 400, origin);
    }
    if (audio.size > MAX_AUDIO_BYTES) {
      return json({ error: "Audio file is too large" }, 413, origin);
    }

    const groqForm = new FormData();
    groqForm.append("file", audio, audio.name || "dictation.wav");
    groqForm.append("model", "whisper-large-v3");
    groqForm.append("response_format", "json");
    groqForm.append("temperature", "0.0");

    const prompt = form.get("prompt");
    if (typeof prompt === "string" && prompt.trim()) {
      groqForm.append("prompt", prompt.trim().slice(0, 900));
    }

    const language = form.get("language");
    if (typeof language === "string" && language.trim()) {
      groqForm.append("language", language.trim().slice(0, 20));
    }

    let groqResponse;
    try {
      groqResponse = await fetch(GROQ_TRANSCRIPTION_URL, {
        method: "POST",
        headers: { Authorization: `Bearer ${env.GROQ_API_KEY}` },
        body: groqForm
      });
    } catch {
      return json({ error: "Unable to reach Groq" }, 502, origin);
    }

    const responseText = await groqResponse.text();
    if (!groqResponse.ok) {
      return json({ error: "Groq transcription failed" }, 502, origin);
    }

    let result;
    try {
      result = JSON.parse(responseText);
    } catch {
      return json({ error: "Invalid response from Groq" }, 502, origin);
    }

    return json({ text: typeof result.text === "string" ? result.text.trim() : "" }, 200, origin);
  }
};
