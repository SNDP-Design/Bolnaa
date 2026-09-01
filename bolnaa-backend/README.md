# Bolnaa API

This Cloudflare Worker keeps the Groq API key off the Android app. It accepts a short multipart audio upload at `POST /transcribe` and proxies it to Groq Whisper Large v3.

## Free setup

1. Install Node.js and Wrangler.
2. Run `wrangler login`.
3. From this directory, run `wrangler secret put GROQ_API_KEY` and paste the Groq key when prompted.
4. Run `npm install`, then `npm run deploy`.
5. Put the resulting Worker `/transcribe` URL in the Android build configuration.

The Worker never returns or logs the Groq key. The Groq key must only be stored as a Cloudflare secret, not in this repository.
