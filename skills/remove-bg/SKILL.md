---
name: remove-bg
description: Remove background from images, producing transparent PNGs. Use when asked to remove, delete, or make transparent the background of an image, cut out a subject, or clean up image edges/borders. Works best on images with light, white, or neutral backgrounds and colorful subjects. NOT for: complex scenes with similar foreground/background colors, or hair/fur matting (use a dedicated ML model for those).
---

# Remove Background

Remove backgrounds from images using saturation/value-based flood fill from edges. Produces clean transparent PNGs with anti-aliased edges.

## How it works

1. Identify background candidates: low saturation + high brightness pixels
2. Flood fill from image edges to find connected background (preserves interior white areas like eyes)
3. Dilate the background mask to eat into the color fringe
4. Remove small isolated islands
5. Anti-alias boundary pixels for smooth edges
6. Trim to bounding box

## Usage

```bash
python3 <skill_dir>/scripts/remove_bg.py input.png output.png [options]
```

Dependencies: `Pillow`, `numpy`, `scipy`. Install with `pip install --break-system-packages Pillow numpy scipy` if missing.

## Tuning parameters

| Parameter         | Default | When to adjust                                                                           |
| ----------------- | ------- | ---------------------------------------------------------------------------------------- |
| `--sat-threshold` | 55      | Raise (60-70) if colored fringe remains; lower (40-50) if subject edges get eaten        |
| `--val-threshold` | 70      | Lower (60) for darker backgrounds; raise (80) for only removing near-white               |
| `--dilate`        | 3       | Increase (4-6) for thicker white halos; decrease (1-2) if subject edges get eroded       |
| `--min-island`    | 50      | Raise to remove larger floating artifacts; lower to keep small details (splatters, dots) |
| `--no-antialias`  | off     | Use when hard edges are preferred (pixel art, icons)                                     |
| `--pad`           | 1       | Padding pixels around the trimmed bounding box                                           |

## Typical workflow

1. Run with defaults first
2. If white halo remains: increase `--dilate` and/or `--sat-threshold`
3. If subject edges get eaten: decrease `--dilate` and `--sat-threshold`
4. If small floating artifacts remain: increase `--min-island`
5. Send result as **document** (not photo) on Telegram to preserve transparency

## Sending result on Telegram

Telegram compresses photos to JPEG (no transparency). Always send as document:

```bash
BOT_TOKEN=$(grep -o '"botToken": *"[^"]*"' ~/.openclaw/openclaw.json | head -1 | sed 's/.*": *"//' | sed 's/"$//')
curl -s "https://api.telegram.org/bot${BOT_TOKEN}/sendDocument" \
  -F "chat_id=<CHAT_ID>" \
  -F "message_thread_id=<TOPIC_ID>" \
  -F "document=@/path/to/output.png" \
  -F "caption=🖼️ Background removed"
```

## Limitations

- Best for light/white/neutral backgrounds with colorful subjects
- Struggles with backgrounds that have similar saturation/value to the subject
- Not suitable for fine detail like hair or fur (use ML-based tools like rembg for those)
- Grayscale subjects on white backgrounds may lose detail
