#!/usr/bin/env python3
"""Package a Hermes-authored runtime directory and generate its update manifest."""
import argparse
import hashlib
import json
import os
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', required=True, help='Directory containing index.html, config.json, css/, js/, modules/')
    parser.add_argument('--output', required=True, help='Server directory, e.g. /srv/homeos/launcher/v1')
    parser.add_argument('--version', required=True, help='Human-readable runtime version, e.g. 1.0.1')
    parser.add_argument('--version-code', required=True, type=int, help='Strictly increasing integer')
    parser.add_argument('--min-shell-version', type=int, default=1)
    args = parser.parse_args()

    source = Path(args.runtime).resolve()
    output = Path(args.output).resolve()
    if not (source / 'index.html').is_file():
        raise SystemExit('Runtime source must contain index.html at its root')
    output.mkdir(parents=True, exist_ok=True)
    bundle = output / 'bundle.zip'
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(source.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(source).as_posix())
    digest = hashlib.sha256(bundle.read_bytes()).hexdigest()
    manifest = {
        'schemaVersion': 1,
        'version': args.version,
        'versionCode': args.version_code,
        'minShellVersion': args.min_shell_version,
        'bundleUrl': '/launcher/v1/bundle.zip',
        'sha256': digest,
    }
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'bundle': str(bundle), 'manifest': str(output / 'manifest.json'), 'sha256': digest}, indent=2))


if __name__ == '__main__':
    main()
