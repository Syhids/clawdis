import { readFile, stat } from "node:fs/promises";
import { extname, isAbsolute, normalize } from "node:path";
import { ErrorCodes, errorShape } from "../protocol/index.js";
import type { GatewayRequestHandlers } from "./types.js";

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

// Only allow TTS audio files under /tmp/openclaw-<uid>/tts-<id>/*
const ALLOWED_PATH_RE = /^\/tmp\/openclaw-[^/]+\/tts-[^/]+\/.+$/;

const MIME_BY_EXT: Record<string, string> = {
  ".mp3": "audio/mpeg",
  ".ogg": "audio/ogg",
  ".wav": "audio/wav",
  ".opus": "audio/opus",
  ".flac": "audio/flac",
  ".m4a": "audio/mp4",
  ".aac": "audio/aac",
  ".webm": "audio/webm",
};

function resolveMimeType(filePath: string): string {
  const ext = extname(filePath).toLowerCase();
  return MIME_BY_EXT[ext] ?? "application/octet-stream";
}

export const fileHandlers: GatewayRequestHandlers = {
  "file.read": async ({ params, respond }) => {
    const path = typeof params.path === "string" ? params.path : "";
    if (!path) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "missing path"));
      return;
    }

    // Security: absolute, no traversal, must match TTS path pattern
    const normalized = normalize(path);
    if (!isAbsolute(normalized)) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "path must be absolute"));
      return;
    }
    if (normalized.includes("..")) {
      respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "path must not contain .."));
      return;
    }
    if (!ALLOWED_PATH_RE.test(normalized)) {
      respond(
        false,
        undefined,
        errorShape(
          ErrorCodes.INVALID_REQUEST,
          "path not allowed: only /tmp/openclaw-*/tts-*/* files are accessible",
        ),
      );
      return;
    }

    try {
      const info = await stat(normalized);
      if (!info.isFile()) {
        respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "not a file"));
        return;
      }
      if (info.size > MAX_FILE_SIZE) {
        respond(
          false,
          undefined,
          errorShape(
            ErrorCodes.INVALID_REQUEST,
            `file too large: ${info.size} bytes (max ${MAX_FILE_SIZE})`,
          ),
        );
        return;
      }

      const data = await readFile(normalized);
      respond(true, {
        base64: data.toString("base64"),
        mimeType: resolveMimeType(normalized),
        size: info.size,
      });
    } catch (err: unknown) {
      const code = (err as NodeJS.ErrnoException).code;
      if (code === "ENOENT") {
        respond(false, undefined, errorShape(ErrorCodes.INVALID_REQUEST, "file not found"));
        return;
      }
      respond(
        false,
        undefined,
        errorShape(
          ErrorCodes.UNAVAILABLE,
          `read failed: ${err instanceof Error ? err.message : String(err)}`,
        ),
      );
    }
  },
};
