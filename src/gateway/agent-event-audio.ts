/**
 * Resolves MEDIA: audio paths in agent events to inline base64 data.
 *
 * When agent events contain `mediaUrls` with local `MEDIA:` audio paths,
 * this module reads the files and returns base64-encoded audio payloads
 * so that remote nodes (Android, iOS) can play them directly from the
 * WebSocket event without needing a separate HTTP download.
 */

import { logVerbose } from "../globals.js";
import { readLocalFileSafely, SafeOpenError } from "../infra/fs-safe.js";
import { detectMime } from "../media/mime.js";

const MEDIA_PREFIX_RE = /^\s*MEDIA\s*:\s*/i;
const AUDIO_EXTENSIONS = new Set(["ogg", "opus", "mp3", "wav", "m4a", "aac", "flac", "webm"]);
const AUDIO_MIME_PREFIX = "audio/";
/** Max audio file size to inline (2 MB). */
const MAX_AUDIO_BYTES = 2 * 1024 * 1024;

export type InlineAudio = {
  base64: string;
  mimeType: string;
  sizeBytes: number;
};

/**
 * Returns true if the URL is a MEDIA:-prefixed path pointing to an audio file.
 */
function isMediaAudioUrl(url: string): boolean {
  if (!MEDIA_PREFIX_RE.test(url)) {
    return false;
  }
  const path = url.replace(MEDIA_PREFIX_RE, "").trim();
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  return AUDIO_EXTENSIONS.has(ext);
}

/**
 * Given a list of mediaUrls, finds MEDIA: audio paths, reads them from disk,
 * and returns an array of inline audio payloads. Returns undefined if no
 * audio could be resolved.
 */
export async function resolveInlineAudio(mediaUrls: unknown): Promise<InlineAudio[] | undefined> {
  if (!Array.isArray(mediaUrls) || mediaUrls.length === 0) {
    return undefined;
  }

  const audioUrls = mediaUrls.filter(
    (u): u is string => typeof u === "string" && isMediaAudioUrl(u),
  );
  if (audioUrls.length === 0) {
    return undefined;
  }

  const results: InlineAudio[] = [];
  for (const url of audioUrls) {
    const filePath = url.replace(MEDIA_PREFIX_RE, "").trim();
    try {
      const { buffer } = await readLocalFileSafely({ filePath, maxBytes: MAX_AUDIO_BYTES });
      const detected = await detectMime({ buffer, filePath });
      const mimeType = detected && detected.startsWith(AUDIO_MIME_PREFIX) ? detected : "audio/ogg";
      results.push({
        base64: buffer.toString("base64"),
        mimeType,
        sizeBytes: buffer.length,
      });
    } catch (err) {
      const label = err instanceof SafeOpenError ? `${err.code}: ${err.message}` : String(err);
      logVerbose(`agent-event-audio: failed to read ${filePath}: ${label}`);
    }
  }

  return results.length > 0 ? results : undefined;
}

/**
 * If the agent event data contains MEDIA: audio URLs, returns an enriched
 * copy of the data with an `audio` field containing base64 payloads.
 * Otherwise returns the data unchanged.
 */
export async function enrichAgentEventWithAudio(
  data: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  const mediaUrls = data.mediaUrls;
  if (!mediaUrls) {
    return data;
  }

  const audio = await resolveInlineAudio(mediaUrls);
  if (!audio) {
    return data;
  }

  return { ...data, audio };
}
