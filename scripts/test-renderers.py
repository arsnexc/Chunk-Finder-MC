#!/usr/bin/env python3
"""CI-only test dependency resolver/downloader. Never bundles third-party mods in our JAR.
Use tests/renderer-lock.json when present; discovery is explicitly recorded until pinned.
"""
import hashlib
import json
import os
from pathlib import Path
import sys
import urllib.parse
import urllib.request

API = 'https://api.modrinth.com/v2/'
HEADERS = {'User-Agent': 'Chunk-Finder-MC compatibility tests (github.com/arsnexc/Chunk-Finder-MC)'}

def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=90) as response:
        return response.read(40 * 1024 * 1024 + 1)

def get(path):
    return json.loads(fetch(API + path))

def select(project):
    query = urllib.parse.urlencode({'game_versions': '["1.21.11"]', 'loaders': '["fabric"]'})
    versions = [v for v in get(f'project/{project}/version?{query}') if v['version_type'] == 'release']
    if not versions:
        raise RuntimeError(f'No stable Fabric 1.21.11 version for {project}')
    return sorted(versions, key=lambda v: v['date_published'], reverse=True)[0]

def entry(version):
    files = [f for f in version['files'] if f['filename'].endswith('.jar')]
    file = next((f for f in files if f.get('primary')), files[0])
    return {'id': version['id'], 'version': version['version_number'], 'url': file['url'],
            'sha512': file['hashes']['sha512'], 'filename': file['filename']}

variant = sys.argv[1]
if variant not in ('vanilla', 'sodium', 'iris'):
    raise SystemExit('Unknown renderer')
output = Path(sys.argv[2]); output.mkdir(parents=True, exist_ok=True)
lockfile = Path('tests/renderer-lock.json')
if lockfile.exists():
    lock = json.loads(lockfile.read_text())
elif variant == "vanilla":
    lock = {"minecraft": "1.21.11", "vanilla": True}
else:
    iris = select('iris'); sodium = select('sodium')
    for dependency in iris['dependencies']:
        if dependency['dependency_type'] == 'required' and dependency.get('version_id'):
            candidate = get('version/' + dependency['version_id'])
            project = get('project/' + candidate['project_id'])
            if project['slug'] == 'sodium':
                sodium = candidate
    lock = {'minecraft': '1.21.11', 'sodium': entry(sodium), 'iris': entry(iris)}
assert lock['minecraft'] == '1.21.11'
(output / 'renderer-lock.json').write_text(json.dumps(lock, indent=2) + '\n')
print('::notice title=Renderer dependency lock::' + json.dumps(lock))
for name in (('sodium', 'iris') if variant == 'iris' else ('sodium',) if variant == 'sodium' else ()):
    item = lock[name]
    data = fetch(item['url'])
    if len(data) > 40 * 1024 * 1024 or hashlib.sha512(data).hexdigest() != item['sha512']:
        raise RuntimeError(f'Invalid artifact/hash for {name}')
    (output / (name + '.jar')).write_bytes(data)
    print(f'Verified test-only {name} {item["version"]}')
