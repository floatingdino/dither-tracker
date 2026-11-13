#!/usr/bin/env python3
"""Summarises a tools/bench.sh run directory as Markdown tables (T20).

    python3 tools/bench_summary.py <outdir>

Reads logcat-play.txt / logcat-fx.txt (PlayView trace: `onDraw ... clip=Rect(l, t - r, b) allocs=N
took Xus`, `touch handled allocs=N`, `MARK <section>`, `fx gallery: <name> for <ms>`), start.txt,
gfx-*.txt, meminfo-*.txt, masks.txt and idle.txt. Missing files are skipped.
"""
import glob
import os
import re
import statistics
import sys

SCREEN = 1872 * 1404
DRAW = re.compile(r"onDraw hw=\w+ clip=Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\) allocs=(-?\d+) took (\d+)us(?: masks=(\d+))?")
INVAL = re.compile(r"inval (?:all|(-?\d+) (-?\d+) (-?\d+) (-?\d+))")
TOUCH = re.compile(r"touch handled allocs=(-?\d+)")
MARK = re.compile(r"MARK (\S+)")
FX = re.compile(r"fx gallery: (\S+) for")


def med(xs):
    return statistics.median(xs) if xs else 0


def sections(path, marker):
    out, cur = {}, None
    order = []
    if not os.path.exists(path):
        return order, out
    for line in open(path, errors="replace"):
        m = marker.search(line)
        if m:
            cur = m.group(1)
            if cur not in out:
                order.append(cur)
                out[cur] = {"draws": [], "touch": [], "inval": []}
            continue
        if cur is None:
            continue
        d = DRAW.search(line)
        if d:
            l, t, r, b, a, us = map(int, d.groups()[:6])
            out[cur]["draws"].append(((r - l) * (b - t), a, us))
            if d.group(7):
                out[cur].setdefault("masks", []).append(int(d.group(7)))
            continue
        v = INVAL.search(line)
        if v:
            out[cur]["inval"].append(SCREEN if v.group(1) is None else (int(v.group(3)) - int(v.group(1))) * (int(v.group(4)) - int(v.group(2))))
            continue
        t = TOUCH.search(line)
        if t:
            out[cur]["touch"].append(int(t.group(1)))
    return order, out


def table(title, order, data):
    prev_masks = [None]
    for s in order:  # the mask count before the first section (its first draw's count)
        ms = data[s].get("masks", [])
        if ms:
            prev_masks[0] = ms[0]
            break
    print(f"\n### {title}\n")
    print("| section | frames | onDraw median µs | max µs | invalidations | invalidated median % | max % | sum (screens) | allocs/draw max | allocs/touch max | masks built in onDraw |")
    print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for s in order:
        d = data[s]["draws"]
        tch = data[s]["touch"]
        if not d and not tch and not data[s]["inval"]:
            print(f"| {s} | 0 | | | | | | | | | |")
            continue
        areas = data[s]["inval"]
        ms = data[s].get("masks", [])
        built = (ms[-1] - prev_masks[0]) if ms and prev_masks[0] is not None else ""
        if ms:
            prev_masks[0] = ms[-1]
        print(
            f"| {s} | {len(d)} | {med([x[2] for x in d]):.0f} | {max([x[2] for x in d], default=0)} | {len(areas)} | "
            f"{100 * med(areas) / SCREEN:.1f} | {100 * max(areas, default=0) / SCREEN:.1f} | {sum(areas) / SCREEN:.2f} | "
            f"{max([x[1] for x in d], default=0)} | {max(tch) if tch else ''} | {built} |"
        )


def gfx(path):
    if not os.path.exists(path):
        return None
    txt = open(path, errors="replace").read()
    def g(rx):
        m = re.search(rx, txt)
        return m.group(1) if m else ""
    return g(r"Total frames rendered: (\d+)"), g(r"50th percentile: (\d+)ms"), g(r"90th percentile: (\d+)ms"), g(r"Janky frames: (\d+)")


def meminfo(path):
    if not os.path.exists(path):
        return None
    txt = open(path, errors="replace").read()
    def row(name):
        m = re.search(r"^\s*" + re.escape(name) + r":\s+(\d+)", txt, re.M)
        return int(m.group(1)) if m else 0
    total = re.search(r"TOTAL PSS:\s+(\d+)", txt) or re.search(r"^\s*TOTAL\s+(\d+)", txt, re.M)
    return {
        "java": row("Java Heap"), "native": row("Native Heap"), "graphics": row("Graphics"),
        "code": row("Code"), "total": int(total.group(1)) if total else 0,
    }


def main():
    d = sys.argv[1]
    print(f"# bench summary: {d}")
    p = os.path.join(d, "start.txt")
    if os.path.exists(p):
        kinds = {}
        for line in open(p):
            k, _, v = line.strip().partition(" ")
            if v.isdigit():
                kinds.setdefault(k, []).append(int(v))
        print("\n### Start (am start -W TotalTime, ms)\n\n| kind | runs | median | min | max |\n|---|---:|---:|---:|---:|")
        for k, v in kinds.items():
            print(f"| {k} | {len(v)} | {med(v):.0f} | {min(v)} | {max(v)} |")
    order, data = sections(os.path.join(d, "logcat-play.txt"), MARK)
    if order:
        table("Play interactions (PlayView trace)", order, data)
        rows = [(s, gfx(os.path.join(d, f"gfx-{s}.txt"))) for s in order]
        rows = [(s, g) for s, g in rows if g]
        if rows:
            print("\n### gfxinfo per section (whole frame incl. RenderThread)\n\n| section | frames | p50 ms | p90 ms | janky |\n|---|---:|---:|---:|---:|")
            for s, g in rows:
                print(f"| {s} | {g[0]} | {g[1]} | {g[2]} | {g[3]} |")
    order, data = sections(os.path.join(d, "logcat-fx.txt"), FX)
    if order:
        table("Effects (debug gallery, all four seats at once)", order, data)
    for name in ("launch", "played"):
        m = meminfo(os.path.join(d, f"meminfo-{name}.txt"))
        if m:
            print(f"\n### Memory after {name} (PSS, KB)\n\n| total | Java heap | native heap | graphics | code |\n|---:|---:|---:|---:|---:|")
            print(f"| {m['total']} | {m['java']} | {m['native']} | {m['graphics']} | {m['code']} |")
    for f in ("start-phases.txt", "masks.txt", "idle.txt", "save-log.txt"):
        p = os.path.join(d, f)
        if os.path.exists(p):
            print(f"\n### {f}\n\n```\n{open(p).read().strip()}\n```")


if __name__ == "__main__":
    main()
