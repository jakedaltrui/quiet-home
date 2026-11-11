#!/usr/bin/env python3
"""Small publication guard, not a complete secret scanner or security audit."""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
files = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
patterns = {
    'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'AWS access key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'Stripe live secret': re.compile(r'\bsk_live_[A-Za-z0-9]{16,}\b'),
    'private machine path': re.compile(r'/(?:Users|home)/[A-Za-z0-9_.-]+/'),
    'LAN address': re.compile(r'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b'),
}
errors = []
for name in filter(None, files):
    path = ROOT / name
    if path.suffix.lower() in {'.keystore', '.jks', '.pem', '.key', '.apk', '.idsig', '.class', '.dex'} or name.startswith(('build/', 'dist/', '.local/', 'firetv/')) or path.name.startswith('.env'):
        errors.append(f'{name}: private or generated file type')
    if path.suffix.lower() in {'.png', '.jpg', '.jpeg', '.mp4', '.mp3', '.ogg', '.woff2'}:
        continue
    try:
        body = path.read_text()
    except UnicodeDecodeError:
        errors.append(f'{name}: unexpected binary file')
        continue
    for label, pattern in patterns.items():
        if pattern.search(body):
            errors.append(f'{name}: {label} pattern detected (value withheld)')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'Publication checks passed for {len([x for x in files if x])} tracked files. Manual review still required.')
