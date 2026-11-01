#!/usr/bin/env python3
"""Build a conservative catalog from HackerTube's attributed public export."""
import argparse
import collections
import json
from pathlib import Path


def build(videos, mentions):
    evidence = collections.defaultdict(list)
    for m in mentions:
        if m['stance'] in ('rec', 'strong_rec'):
            evidence[m['vid']].append(m)
    result = []
    for v in videos:
        recs = evidence[v['vid']]
        months = {m['month'] for m in recs}
        years = {m['month'][:4] for m in recs}
        authors = {m['author'] for m in recs}
        if len(months) < 3 or len(years) < 2 or len(authors) < 3:
            continue
        if v['shelf_life'] == 'news':
            continue
        result.append(dict(id=v['vid'], title=v['title'], channel=v['channel'],
            summary=v['summary'], why=v['why_hn'], score=v['score'],
            topics=sorted({c['area_name'] for c in v['categories']}),
            months=len(months), recommenders=len(authors),
            first=min(years), last=max(years),
            evidence=[dict(id=m['hn_comment_id'], author=m['author'], month=m['month'],
                           quote=m.get('quote') or m['why']) for m in sorted(recs, key=lambda m: m['month'])]))
    return sorted(result, key=lambda v: (-v['score'], v['title']))


def lines(path):
    with open(path) as f:
        return [json.loads(line) for line in f if line.strip()]


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('videos'); p.add_argument('mentions')
    p.add_argument('--output', default='app/src/main/assets/catalog.json')
    args = p.parse_args()
    data = build(lines(args.videos), lines(args.mentions))
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')))
    print(f'Exported {len(data)} repeatedly recommended videos')
