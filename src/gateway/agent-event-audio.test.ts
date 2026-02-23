import { describe, expect, it, vi } from "vitest";
import { enrichAgentEventWithAudio, resolveInlineAudio } from "./agent-event-audio.js";

// Mock readLocalFileSafely and detectMime
vi.mock("../infra/fs-safe.js", () => ({
  readLocalFileSafely: vi.fn(),
  SafeOpenError: class SafeOpenError extends Error {
    code: string;
    constructor(code: string, message: string) {
      super(message);
      this.code = code;
      this.name = "SafeOpenError";
    }
  },
}));

vi.mock("../media/mime.js", () => ({
  detectMime: vi.fn(),
}));

vi.mock("../globals.js", () => ({
  logVerbose: vi.fn(),
}));

import { readLocalFileSafely } from "../infra/fs-safe.js";
import { detectMime } from "../media/mime.js";

describe("resolveInlineAudio", () => {
  it("returns undefined for empty mediaUrls", async () => {
    expect(await resolveInlineAudio([])).toBeUndefined();
    expect(await resolveInlineAudio(null)).toBeUndefined();
    expect(await resolveInlineAudio(undefined)).toBeUndefined();
  });

  it("returns undefined for non-MEDIA URLs", async () => {
    expect(await resolveInlineAudio(["https://example.com/audio.ogg"])).toBeUndefined();
  });

  it("returns undefined for non-audio MEDIA URLs", async () => {
    expect(await resolveInlineAudio(["MEDIA:/tmp/screenshot.png"])).toBeUndefined();
  });

  it("resolves MEDIA: audio paths to base64", async () => {
    const audioBuffer = Buffer.from("fake-ogg-audio-data");
    vi.mocked(readLocalFileSafely).mockResolvedValue({
      buffer: audioBuffer,
      realPath: "/tmp/tts.ogg",
      stat: { size: audioBuffer.length } as unknown,
    });
    vi.mocked(detectMime).mockResolvedValue("audio/ogg");

    const result = await resolveInlineAudio(["MEDIA:/tmp/tts.ogg"]);
    expect(result).toHaveLength(1);
    expect(result![0]).toEqual({
      base64: audioBuffer.toString("base64"),
      mimeType: "audio/ogg",
      sizeBytes: audioBuffer.length,
    });
    expect(readLocalFileSafely).toHaveBeenCalledWith({
      filePath: "/tmp/tts.ogg",
      maxBytes: 2 * 1024 * 1024,
    });
  });

  it("handles MEDIA: with extra whitespace", async () => {
    const audioBuffer = Buffer.from("test");
    vi.mocked(readLocalFileSafely).mockResolvedValue({
      buffer: audioBuffer,
      realPath: "/tmp/voice.opus",
      stat: { size: audioBuffer.length } as unknown,
    });
    vi.mocked(detectMime).mockResolvedValue("audio/opus");

    const result = await resolveInlineAudio(["  MEDIA : /tmp/voice.opus"]);
    expect(result).toHaveLength(1);
    expect(result![0].mimeType).toBe("audio/opus");
  });

  it("falls back to audio/ogg when mime is not audio", async () => {
    const audioBuffer = Buffer.from("data");
    vi.mocked(readLocalFileSafely).mockResolvedValue({
      buffer: audioBuffer,
      realPath: "/tmp/tts.ogg",
      stat: { size: audioBuffer.length } as unknown,
    });
    vi.mocked(detectMime).mockResolvedValue("application/octet-stream");

    const result = await resolveInlineAudio(["MEDIA:/tmp/tts.ogg"]);
    expect(result![0].mimeType).toBe("audio/ogg");
  });

  it("skips files that fail to read", async () => {
    vi.mocked(readLocalFileSafely).mockRejectedValue(new Error("file not found"));

    const result = await resolveInlineAudio(["MEDIA:/tmp/missing.ogg"]);
    expect(result).toBeUndefined();
  });

  it("supports multiple audio extensions", async () => {
    const audioBuffer = Buffer.from("mp3-data");
    vi.mocked(readLocalFileSafely).mockResolvedValue({
      buffer: audioBuffer,
      realPath: "/tmp/tts.mp3",
      stat: { size: audioBuffer.length } as unknown,
    });
    vi.mocked(detectMime).mockResolvedValue("audio/mpeg");

    const result = await resolveInlineAudio(["MEDIA:/tmp/tts.mp3"]);
    expect(result).toHaveLength(1);
    expect(result![0].mimeType).toBe("audio/mpeg");
  });
});

describe("enrichAgentEventWithAudio", () => {
  it("returns data unchanged when no mediaUrls", async () => {
    const data = { text: "hello", delta: "hello" };
    const result = await enrichAgentEventWithAudio(data);
    expect(result).toBe(data);
  });

  it("enriches data with audio when MEDIA: audio URLs present", async () => {
    const audioBuffer = Buffer.from("ogg-audio");
    vi.mocked(readLocalFileSafely).mockResolvedValue({
      buffer: audioBuffer,
      realPath: "/tmp/voice.ogg",
      stat: { size: audioBuffer.length } as unknown,
    });
    vi.mocked(detectMime).mockResolvedValue("audio/ogg");

    const data = {
      text: "Here is the audio",
      delta: "audio",
      mediaUrls: ["MEDIA:/tmp/voice.ogg"],
    };
    const result = await enrichAgentEventWithAudio(data);
    expect(result).not.toBe(data);
    expect(result.text).toBe("Here is the audio");
    expect(result.audio).toHaveLength(1);
    expect((result.audio as Array<{ mimeType: string }>)[0].mimeType).toBe("audio/ogg");
  });

  it("returns data unchanged when mediaUrls has no audio", async () => {
    const data = {
      text: "screenshot",
      mediaUrls: ["MEDIA:/tmp/screenshot.png"],
    };
    const result = await enrichAgentEventWithAudio(data);
    expect(result).toBe(data);
  });
});
