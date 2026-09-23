#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建背单词/背动词数据资产。

数据来源：
- android/app/src/main/assets/dictionary.db （本地词典，word/pos/zh/en）
- tools/data/fr_freq_50k.txt （法语词频表，仅用于推导 CEFR/专八分级）
  词频表来源：https://github.com/hermitdave/FrequencyWords (CC-BY-SA)

分级策略：
- 命中词频表的词按频率排名映射到 A1/A2/B1/B2/C1/C2；
- 未命中词频表的词归入最高档「专八」（生僻但仍在词典收录范围内的进阶词）。

产物：
- android/app/src/main/assets/vocab/vocab.json
  结构：{"version":2, "generated":..., "levels":[[id,label]...],
         "words":[[word,pos,level,meaning,isVerb01], ...]}

用法：
    python3 tools/build_vocab_assets.py
"""
import json
import os
import re
import sqlite3
import sys
import unicodedata
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(ROOT, "android/app/src/main/assets/dictionary.db")
FREQ_PATH = os.path.join(ROOT, "tools/data/fr_freq_50k.txt")
OUT_PATH = os.path.join(ROOT, "android/app/src/main/assets/vocab/vocab.json")

# ---------------------------------------------------------------- 分级
LEVELS = [
    ("A1", "A1"),
    ("A2", "A2"),
    ("B1", "B1"),
    ("B2", "B2"),
    ("C1", "C1"),
    ("C2", "C2"),
    ("S8", "专八"),
]
LEVEL_RANKS = [
    ("A1", 900),
    ("A2", 2200),
    ("B1", 5000),
    ("B2", 10000),
    ("C1", 20000),
    ("C2", 50000),
]
RARE_LEVEL = "S8"

CJK = re.compile(r'[\u4e00-\u9fff]')

# ---------------------------------------------------------------- 释义清洗
POS_TOK = re.compile(
    r'(?:et\s+)?(?:vt|vi|vpr|vbl|vtr|n|v|a|adj|adv|m|f|pl|prep|conj|pron|interj|num|art|loc)\.[a-z.]*',
    re.I)
PAREN = re.compile(r'[（(][^）)]*[）)]')
BRACKET = re.compile(r'[\[【][^\]】]*[\]】]')
ANGLE = re.compile(r'<[^>]*>')
CIRC = re.compile(r'[①-⑳]')
POS_PREFIX = re.compile(
    r'^\s*(?:(?:et\s+)?(?:vt|vi|vpr|vbl|vtr|n|v|a|adj|adv|m|f|pl|prep|conj|pron|interj|num|art|loc)\.[a-z.]*\s*)+',
    re.I)
LEAD_MARK = re.compile(r'^\s*(?:[Iil1]+\.\s*)+')
MID_MARK = re.compile(r'(?<=[\s,，、；;])[Iil1]+\.\s*')


def _strip_noise(zh: str) -> str:
    s = PAREN.sub('', zh)
    s = BRACKET.sub('', s)
    s = ANGLE.sub('', s)
    s = CIRC.sub(' ', s)
    s = POS_PREFIX.sub('', s)
    s = LEAD_MARK.sub('', s)
    s = MID_MARK.sub(' ', s)
    s = POS_TOK.sub(' ', s)
    return re.sub(r'\s+', ' ', s).strip(' ,;，；、:：。.')


def clean_meaning(zh: str) -> str:
    if not zh:
        return ""
    s = re.split(r'[;；]', _strip_noise(zh))[0]
    parts = [p.strip(' ,.。:：') for p in re.split(r'[,，、\s]+', s)]
    seen = set()
    uniq = []
    for p in parts:
        if p and p not in seen:
            seen.add(p)
            uniq.append(p)
    out = '，'.join(uniq[:2]) if uniq else ''
    if len(out) > 30:
        out = out[:30]
    return out


def valid_meaning(m: str) -> bool:
    n = len(CJK.findall(m))
    if n == 0:
        return False
    if '~' in m:
        return False
    if m.startswith('的'):
        return False
    return True


WORD_RE = re.compile(r"^[a-zà-öø-ÿœæ][a-zà-öø-ÿœæ'\-]{1,19}$")
VERB_TAGS = ('v.', 'v.1', 'v.2', 'v.3', 'v.t.', 'v.i.', 'v.pr.', 'v.impers.',
             'v.pron.', 'v.aux.', 'v.intr.', 'v.tr.', 'vt.', 'vi.', 'vt.indir.',
             'v.imp.', 'v.n.')
NONVERB_TAGS = ('n.', 'n.m.', 'n.f.', 'n.m.pl', 'n.f.pl', 'n.m.&adj.', 'n.f.&adj.',
                'adj.', 'adj', 'a.', 'adv.', 'adv', 'noun', 'num', 'num.', 'intj',
                'intj.', 'pron.', 'pron', 'prep.', 'prep', 'conj.', 'conj',
                'n.m.inv.', 'n.m.pl.', 'n.f.pl.', 'n.m.&n.f.', 'n.m.&adj.',
                'n.inv.', 'n.f.inv.', 'adj.inv.', 'adj.&n.m.', 'adj.&pron.',
                'loc.adv.', 'loc.adj.', 'loc.conj.', 'loc.prep.', 'adv.&adj.')

INFINITIVE_END = ('er', 'ir', 're', 'oir')


def norm(s: str) -> str:
    s = s.lower().strip()
    s = unicodedata.normalize('NFD', s)
    return ''.join(ch for ch in s if unicodedata.category(ch) != 'Mn')


def load_freq():
    freq = {}
    with open(FREQ_PATH, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.rsplit(' ', 1)
            if len(parts) != 2:
                continue
            try:
                freq[parts[0]] = int(parts[1])
            except ValueError:
                continue
    ordered = sorted(freq.items(), key=lambda kv: -kv[1])
    return {w: i for i, (w, _) in enumerate(ordered, 1)}


def level_of(rank):
    if rank is None:
        return RARE_LEVEL
    for lvl, hi in LEVEL_RANKS:
        if rank <= hi:
            return lvl
    return RARE_LEVEL


def main():
    if not os.path.exists(DB_PATH):
        print("缺少词典数据库:", DB_PATH, file=sys.stderr)
        return 1
    if not os.path.exists(FREQ_PATH):
        print("缺少词频表:", FREQ_PATH, file=sys.stderr)
        return 1

    rank = load_freq()
    db = sqlite3.connect(DB_PATH)
    cur = db.cursor()

    def query_words(tags, ending_filter=False):
        rows = cur.execute(
            "SELECT word, pos, zh FROM dict WHERE pos IN (%s)" %
            ",".join("?" * len(tags)), tuple(tags)).fetchall()
        out = {}
        for word, pos, zh in rows:
            w = (word or '').strip().lower()
            if not WORD_RE.match(w):
                continue
            if ending_filter and not w.endswith(INFINITIVE_END):
                continue
            good = valid_meaning(clean_meaning(zh))
            prev = out.get(w)
            if prev is None or (not valid_meaning(clean_meaning(prev[1])) and good):
                out[w] = (pos, zh)
        return out

    verb_rows = query_words(VERB_TAGS, ending_filter=True)
    noun_rows = query_words(NONVERB_TAGS)

    words = []
    stats = {"verb": 0, "word": 0, "bad": 0, "nofreq": 0}

    def emit(w, pos, zh, is_verb):
        r = rank.get(norm(w))
        if r is None:
            stats["nofreq"] += 1
        m = clean_meaning(zh)
        if not valid_meaning(m):
            stats["bad"] += 1
            return
        stats["verb" if is_verb else "word"] += 1
        words.append((w, pos, level_of(r), m, 1 if is_verb else 0))

    for w, (pos, zh) in verb_rows.items():
        emit(w, pos, zh, True)
    for w, (pos, zh) in noun_rows.items():
        emit(w, pos, zh, False)

    words.sort(key=lambda x: (x[2], x[0]))
    out = {
        "version": 2,
        "generated": date.today().isoformat(),
        "levels": [[lid, label] for lid, label in LEVELS],
        "words": [[w, p, lvl, z, v] for w, p, lvl, z, v in words],
    }
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    tmp = OUT_PATH + ".tmp"
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(tmp, OUT_PATH)

    from collections import Counter
    by_level = Counter(w[2] for w in words)
    by_kind = Counter("verb" if w[4] else "word" for w in words)
    print("写入:", OUT_PATH)
    print("总词数:", len(words), "大小: %.1f KB" % (os.path.getsize(OUT_PATH) / 1024))
    print("分级:", {lid: by_level.get(lid, 0) for lid, _ in LEVELS})
    print("词/动词:", dict(by_kind))
    print("跳过(释义不合格):", stats["bad"], " (其中无词频):", stats["nofreq"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
