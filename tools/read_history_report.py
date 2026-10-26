#!/usr/bin/env python3
"""Decode a completed [Storico WO] issue into verified JSON for analysis."""
import argparse, base64, hashlib, json, re, urllib.request

REPO = 'ButtiGit/ButtiGoGym'
def read(path):
    request = urllib.request.Request('https://api.github.com/repos/' + REPO + '/' + path,
        headers={'Accept': 'application/vnd.github+json', 'User-Agent': 'ButtiGoGym-History-Reader'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)

def decode(issue_number):
    issue = read(f'issues/{issue_number}')
    digest = re.search(r'SHA-256.*?`([a-f0-9]{64})`', issue['body']).group(1)
    parts, complete, page = {}, False, 1
    while True:
        comments = read(f'issues/{issue_number}/comments?per_page=100&page={page}')
        for comment in comments:
            body = comment['body']
            complete |= f'<!-- buttigogym:{digest}:complete -->' in body
            match = re.search(r'<!-- buttigogym:' + digest + r':part:(\d+) -->.*?```base64\s+([A-Za-z0-9+/=\s]+)\s*```', body, re.S)
            if match:
                number = int(match[1]); value = base64.b64decode(re.sub(r'\s+', '', match[2]), validate=True)
                if number in parts and parts[number] != value:
                    raise ValueError('Conflicting part data')
                parts[number] = value
        if len(comments) < 100: break
        page += 1
    expected = int(re.search(r'Parti attese: (\d+)', issue['body'])[1])
    if not complete or set(parts) != set(range(1, expected + 1)):
        raise ValueError('Upload incomplete; resume it from the app')
    raw = b''.join(parts[index] for index in range(1, expected + 1))
    if hashlib.sha256(raw).hexdigest() != digest: raise ValueError('Archive hash mismatch')
    data = json.loads(raw)
    if data.get('schema') != 'buttigogym-workout-history-v1': raise ValueError('Unknown schema')
    return data

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('issue', type=int)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    with open(args.output, 'w', encoding='utf-8') as out:
        json.dump(decode(args.issue), out, ensure_ascii=False, indent=2)
