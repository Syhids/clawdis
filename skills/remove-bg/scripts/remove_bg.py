#!/usr/bin/env python3
"""Remove background from an image using saturation/value flood fill.

Works best on images with light/white/neutral backgrounds and colorful subjects.
Preserves interior low-saturation regions (e.g. eyes) by only removing
background connected to image edges.

Dependencies: Pillow, numpy, scipy (install via pip if missing).

Usage:
    python remove_bg.py input.png output.png [options]

Options:
    --sat-threshold  Max saturation (0-100) for background candidates (default: 55)
    --val-threshold  Min value/brightness (0-100) for background candidates (default: 70)
    --dilate         Dilation iterations to eat into fringe (default: 3)
    --min-island     Minimum pixel count to keep a subject component (default: 50)
    --no-antialias   Disable edge anti-aliasing
    --pad            Padding pixels around trimmed result (default: 1)
"""

import argparse
import sys

import numpy as np
from PIL import Image
from scipy import ndimage


def compute_sat_val(data: np.ndarray):
    """Compute saturation (0-100) and value (0-100) from RGBA data."""
    r, g, b = data[:, :, 0].astype(float), data[:, :, 1].astype(float), data[:, :, 2].astype(float)
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = np.where(mx > 0, (mx - mn) / mx * 100, 0)
    val = mx / 255 * 100
    return sat, val


def remove_background(
    img: Image.Image,
    sat_threshold: float = 55,
    val_threshold: float = 70,
    dilate: int = 3,
    min_island: int = 50,
    antialias: bool = True,
    pad: int = 1,
) -> Image.Image:
    """Remove background from a PIL Image, return RGBA with transparency."""
    img = img.convert("RGBA")
    data = np.array(img)
    h, w = data.shape[:2]
    a = data[:, :, 3]
    sat, val = compute_sat_val(data)

    # Background candidates: low saturation + high brightness + opaque
    bg_candidates = (sat < sat_threshold) & (val > val_threshold) & (a > 0)

    # Flood fill from edges — only remove bg connected to border
    edge_mask = np.zeros((h, w), dtype=bool)
    edge_mask[0, :] = True
    edge_mask[-1, :] = True
    edge_mask[:, 0] = True
    edge_mask[:, -1] = True

    labeled, _ = ndimage.label(bg_candidates)
    seed = edge_mask & bg_candidates
    edge_labels = set(labeled[seed].flatten()) - {0}
    bg_connected = np.isin(labeled, list(edge_labels))

    # Dilate background into the fringe, but only where sat is still low
    if dilate > 0:
        bg_dilated = ndimage.binary_dilation(bg_connected, iterations=dilate)
        bg_final = bg_dilated & (sat < sat_threshold + 5)
    else:
        bg_final = bg_connected

    # Find subject components, remove small islands
    subject_mask = ~bg_final & (a > 0)
    subject_labeled, subject_features = ndimage.label(subject_mask)
    sizes = ndimage.sum(subject_mask, subject_labeled, range(1, subject_features + 1))
    large_components = [i + 1 for i, s in enumerate(sizes) if s > min_island]
    clean_subject = np.isin(subject_labeled, large_components)

    # Build result
    result = data.copy()
    result[~clean_subject, 3] = 0

    # Anti-alias boundary pixels
    if antialias:
        eroded = ndimage.binary_erosion(clean_subject, iterations=1)
        boundary = clean_subject & ~eroded
        low_sat_boundary = boundary & (sat < 40)
        alpha_factor = np.clip((sat[low_sat_boundary] - 10) / 30, 0.05, 1)
        result[low_sat_boundary, 3] = (alpha_factor * result[low_sat_boundary, 3]).astype(np.uint8)

    # Trim to bounding box of non-transparent pixels
    non_t = result[:, :, 3] > 5
    rows = np.any(non_t, axis=1)
    cols = np.any(non_t, axis=0)
    if not rows.any():
        return Image.fromarray(result)
    rmin, rmax = np.where(rows)[0][[0, -1]]
    cmin, cmax = np.where(cols)[0][[0, -1]]
    rmin = max(0, rmin - pad)
    rmax = min(h - 1, rmax + pad)
    cmin = max(0, cmin - pad)
    cmax = min(w - 1, cmax + pad)
    trimmed = result[rmin : rmax + 1, cmin : cmax + 1]

    return Image.fromarray(trimmed)


def main():
    parser = argparse.ArgumentParser(description="Remove background from an image")
    parser.add_argument("input", help="Input image path")
    parser.add_argument("output", help="Output PNG path")
    parser.add_argument("--sat-threshold", type=float, default=55, help="Saturation threshold (0-100)")
    parser.add_argument("--val-threshold", type=float, default=70, help="Value/brightness threshold (0-100)")
    parser.add_argument("--dilate", type=int, default=3, help="Dilation iterations for fringe removal")
    parser.add_argument("--min-island", type=int, default=50, help="Minimum island size to keep")
    parser.add_argument("--no-antialias", action="store_true", help="Disable edge anti-aliasing")
    parser.add_argument("--pad", type=int, default=1, help="Padding around trimmed result")
    args = parser.parse_args()

    img = Image.open(args.input)
    result = remove_background(
        img,
        sat_threshold=args.sat_threshold,
        val_threshold=args.val_threshold,
        dilate=args.dilate,
        min_island=args.min_island,
        antialias=not args.no_antialias,
        pad=args.pad,
    )
    result.save(args.output, "PNG")
    print(f"OK: {img.size[0]}x{img.size[1]} → {result.size[0]}x{result.size[1]}")


if __name__ == "__main__":
    main()
