#!/usr/bin/env python3
"""Render the checked Hyperframes timeline without a localhost HTTP listener.

Requires Node.js 22+, Hyperframes 0.8.222, FFmpeg and FFprobe.
Use the normal Hyperframes render command when your environment permits it.
"""
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSITION = ROOT / 'brag-output' / 'composition'
MEDIA = ROOT / 'docs' / 'media'
CLI = ['npx', 'hyperframes@0.8.222']
FPS, FRAMES = 30, 660


def run(args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


MEDIA.mkdir(parents=True, exist_ok=True)
run(CLI + ['check', COMPOSITION, '--json'])
with tempfile.TemporaryDirectory(prefix='quiet-home-video-') as directory:
    temporary = Path(directory)
    captures = temporary / 'captures'
    run(CLI + ['snapshot', COMPOSITION, '--range', '0,21.966666667',
               '--frames', str(FRAMES), '--keep', '--no-end', '--output', captures])
    images = sorted(captures.glob('at-*s.png'), key=lambda p: float(p.stem[3:-1]))
    if len(images) != FRAMES:
        raise SystemExit(f'Expected {FRAMES} frames, got {len(images)}')
    for i, image in enumerate(images):
        (temporary / f'frame-{i:04d}.png').symlink_to(image)
    assets = COMPOSITION / 'assets'
    silent = temporary / 'render.mp4'
    run(['ffmpeg', '-y', '-framerate', FPS, '-i', temporary / 'frame-%04d.png',
         '-i', assets / 'music.mp3', '-i', assets / 'click.ogg',
         '-filter_complex',
         '[1:a]atrim=0:22,asetpts=PTS-STARTPTS,volume=0.20,'
         'afade=t=in:d=0.5,afade=t=out:st=20.7:d=1.3[m];'
         '[2:a]asplit=2[c1][c2];[c1]volume=0.3,adelay=4640|4640[a1];'
         '[c2]volume=0.3,adelay=6280|6280[a2];'
         '[m][a1][a2]amix=inputs=3:duration=first:normalize=0[a]',
         '-map', '0:v', '-map', '[a]', '-c:v', 'libx264', '-crf', '18',
         '-preset', 'slow', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k',
         '-t', '22', '-movflags', '+faststart', silent])
    run(['ffmpeg', '-y', '-ss', '6', '-i', silent, '-frames:v', '1', '-q:v', '2', MEDIA / 'brag.jpg'])
    run(['ffmpeg', '-y', '-ss', '4', '-i', silent, '-frames:v', '1', '-q:v', '2', MEDIA / 'hero.jpg'])
    run(['ffmpeg', '-y', '-i', silent, '-i', MEDIA / 'brag.jpg', '-filter_complex',
         "[0:v][1:v]overlay=0:0:enable='eq(n,0)'[v]", '-map', '[v]', '-map', '0:a',
         '-c:v', 'libx264', '-crf', '18', '-preset', 'slow', '-pix_fmt', 'yuv420p',
         '-c:a', 'copy', '-movflags', '+faststart', MEDIA / 'brag.mp4'])
print(MEDIA / 'brag.mp4')
