"""
Generate a tick-ring PNG (watch-face index) at pixel-perfect quality.

Renders at 4x supersampling and downsamples with LANCZOS for crisp anti-aliasing.
Ticks are drawn as exact rectangles in polar space, so edges are geometric (no font/stroke
rounding artifacts) — this is what makes it "perfect."

Defaults reproduce a 60-tick uniform ring matching the radial position of
`index_small_seconds_0` but without the 12 hour-position gaps.

Usage:
    python tools/generate_index.py
    python tools/generate_index.py --out app/src/main/res/drawable-nodpi/index_seconds_full.png
    python tools/generate_index.py --major-every 5 --major-extra-len 7
"""

import argparse
import math
from PIL import Image, ImageDraw


def render(
    size: int = 450,
    tick_count: int = 60,
    inner_radius: float = 204.0,
    outer_radius: float = 216.0,
    tick_width_px: float = 3.5,
    color: tuple = (255, 255, 255, 255),
    major_every: int = 0,        # 0 disables major ticks; e.g. 5 = every 5th tick longer
    major_extra_len: float = 0,  # extra px the major tick extends inward
    skip_indices: tuple = (),    # tick indices (0..tick_count-1) to omit (e.g. for hour gaps)
    rotation_deg: float = 0,     # rotate the whole ring; 0 = first tick at 12 o'clock
    cap: str = "butt",           # "butt" (square) or "round" (capsule/pill)
    supersample: int = 4,
) -> Image.Image:
    ss = supersample
    big = size * ss
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx = cy = big / 2.0
    step = 360.0 / tick_count

    for i in range(tick_count):
        if i in skip_indices:
            continue
        deg = rotation_deg + i * step
        # 0 deg = 12 o'clock (top), clockwise
        ang = math.radians(deg - 90.0)
        is_major = major_every > 0 and (i % major_every == 0)
        r_in = (inner_radius - (major_extra_len if is_major else 0)) * ss
        r_out = outer_radius * ss
        half_w = (tick_width_px * ss) / 2.0

        # Tangent unit vector (perpendicular to radius), then radial unit vector:
        tx, ty = -math.sin(ang), math.cos(ang)
        rx, ry = math.cos(ang), math.sin(ang)

        if cap == "round":
            # Capsule: rectangle inset by half_w on each end + two end-circles.
            # Total visible extent stays r_in..r_out.
            r_in_body = r_in + half_w
            r_out_body = r_out - half_w
            if r_out_body > r_in_body:
                p_in_l = (cx + rx * r_in_body - tx * half_w, cy + ry * r_in_body - ty * half_w)
                p_in_r = (cx + rx * r_in_body + tx * half_w, cy + ry * r_in_body + ty * half_w)
                p_out_r = (cx + rx * r_out_body + tx * half_w, cy + ry * r_out_body + ty * half_w)
                p_out_l = (cx + rx * r_out_body - tx * half_w, cy + ry * r_out_body - ty * half_w)
                draw.polygon([p_in_l, p_in_r, p_out_r, p_out_l], fill=color)
            # End caps (always drawn — also handles the degenerate case where length < width)
            for r_cap in (r_in + half_w, r_out - half_w):
                ccx = cx + rx * r_cap
                ccy = cy + ry * r_cap
                draw.ellipse(
                    [ccx - half_w, ccy - half_w, ccx + half_w, ccy + half_w],
                    fill=color,
                )
        else:
            # Butt cap: plain rectangle.
            p_in_l = (cx + rx * r_in - tx * half_w, cy + ry * r_in - ty * half_w)
            p_in_r = (cx + rx * r_in + tx * half_w, cy + ry * r_in + ty * half_w)
            p_out_r = (cx + rx * r_out + tx * half_w, cy + ry * r_out + ty * half_w)
            p_out_l = (cx + rx * r_out - tx * half_w, cy + ry * r_out - ty * half_w)
            draw.polygon([p_in_l, p_in_r, p_out_r, p_out_l], fill=color)

    return img.resize((size, size), Image.LANCZOS)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--out", default="index_out.png")
    p.add_argument("--size", type=int, default=450)
    p.add_argument("--ticks", type=int, default=60)
    p.add_argument("--inner-radius", type=float, default=204.0)
    p.add_argument("--outer-radius", type=float, default=216.0)
    p.add_argument("--width", type=float, default=3.5, help="tick width in px (perpendicular)")
    p.add_argument("--major-every", type=int, default=0,
                   help="every Nth tick is longer (0 = no majors). Use 5 for hour markers.")
    p.add_argument("--major-extra-len", type=float, default=0,
                   help="extra inward length for major ticks, in px")
    p.add_argument("--skip", type=str, default="",
                   help="comma-separated tick indices to omit, e.g. 0,5,10,...,55")
    p.add_argument("--rotation", type=float, default=0)
    p.add_argument("--cap", choices=["butt", "round"], default="butt",
                   help="tick end style: butt (square) or round (capsule/pill)")
    args = p.parse_args()

    skip = tuple(int(s) for s in args.skip.split(",") if s.strip())
    img = render(
        size=args.size,
        tick_count=args.ticks,
        inner_radius=args.inner_radius,
        outer_radius=args.outer_radius,
        tick_width_px=args.width,
        major_every=args.major_every,
        major_extra_len=args.major_extra_len,
        skip_indices=skip,
        rotation_deg=args.rotation,
        cap=args.cap,
    )
    img.save(args.out)
    print(f"wrote {args.out} ({img.size[0]}x{img.size[1]})")


if __name__ == "__main__":
    main()
