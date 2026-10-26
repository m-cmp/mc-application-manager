"""Patch the pinned Nexus 3.97 UI while preserving the original security flow."""
import argparse
import hashlib
import io
from pathlib import Path
import zipfile

ORIGINAL_SHA256 = '08d68be4cfb134f9ddc990bf465e22cf224212436518f04efd0034f5841c487b'
NESTED = 'BOOT-INF/lib/nexus-rapture-3.97.0-13.jar'
REVISION = b'fix3'


def patch(source: Path, target: Path) -> None:
    if source.resolve() == target.resolve():
        raise ValueError('Source and output must be different files')
    if hashlib.sha256(source.read_bytes()).hexdigest() != ORIGINAL_SHA256:
        raise ValueError('Expected the original Nexus 3.97.0-13 JAR; refusing an unknown version')
    transport = (Path(__file__).parent / 'extdirect-transport.js').read_bytes()
    replacements = {
        'org/sonatype/nexus/rapture/internal/index.vm':
            (b'$src?$urlSuffix', b'$src?$urlSuffix&_mcmpUi=' + REVISION),
        'org/sonatype/nexus/rapture/internal/bootstrap.vm':
            (b"'$urlSuffix'", b"'$urlSuffix&_mcmpUi=" + REVISION + b"'"),
    }
    changed = set()
    with zipfile.ZipFile(source) as outer:
        buffer = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(outer.read(NESTED))) as original, zipfile.ZipFile(buffer, 'w') as result:
            for entry in original.infolist():
                content = original.read(entry.filename)
                if entry.filename == 'static/rapture/nexus-rapture-prod.js':
                    content = transport + b'\n' + content
                    changed.add(entry.filename)
                if entry.filename in replacements:
                    old, new = replacements[entry.filename]
                    if old not in content:
                        raise ValueError('Expected template marker is absent: ' + entry.filename)
                    content = content.replace(old, new)
                    changed.add(entry.filename)
                result.writestr(entry, content)
        if len(changed) != 3:
            raise ValueError('Unexpected resource layout: ' + repr(changed))
        with zipfile.ZipFile(target, 'w') as result:
            for entry in outer.infolist():
                result.writestr(entry, buffer.getvalue() if entry.filename == NESTED else outer.read(entry.filename))
    print('Patched JAR SHA256:', hashlib.sha256(target.read_bytes()).hexdigest())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('target', type=Path)
    args = parser.parse_args()
    patch(args.source, args.target)
