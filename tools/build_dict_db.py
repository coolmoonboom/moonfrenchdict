#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
构建预编译词典数据库 dictionary.db。

输入: tools/data/word.sj
      JSON 数组，每条为 [word, pos, en, zh]
输出: android/app/src/main/assets/dictionary.db
      含 dict 表（word/norm/pos/meaning/zh/en/stem）+ 3-gram 倒排表 + meta 表

用法:
    python3 tools/build_dict_db.py
"""
import json
import os
import sqlite3
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "android", "app", "src", "main", "assets")
WORD_SJ = os.path.join(BASE_DIR, "tools", "data", "word.sj")
OUT_DB = os.path.join(ASSETS_DIR, "dictionary.db")

ACCENT_MAP = {
    'à': 'a', 'â': 'a', 'ä': 'a', 'æ': 'a',
    'é': 'e', 'è': 'e', 'ê': 'e', 'ë': 'e',
    'î': 'i', 'ï': 'i',
    'ô': 'o', 'ö': 'o', 'œ': 'o',
    'ù': 'u', 'û': 'u', 'ü': 'u',
    'ÿ': 'y',
    'ç': 'c',
}

SUFFIXES = [
    "ables", "ation", "ement", "ement", "ité", "iste", "isme",
    "erait", "irait", "aient", "ions", "iez",
    "ant", "ent", "ait", "ait",
    "able", "ible", "euse", "eux", "eur", "esse", "aire",
    "ique", "ette", "eaux", "eau",
    "er", "ir", "re", "e", "s", "x", "z",
]


def normalize(s: str) -> str:
    return ''.join(ACCENT_MAP.get(c, c) for c in s.lower())


def stem(word: str) -> str:
    w = normalize(word)
    for sfx in SUFFIXES:
        if len(w) > len(sfx) + 3 and w.endswith(sfx):
            w = w[:-len(sfx)]
            break
    return w


def trigrams(norm: str):
    """生成 3-gram：两端补空白确保短词也有 gram。"""
    s = ' ' + norm + ' '
    for i in range(len(s) - 2):
        yield s[i:i + 3]


def build():
    with open(WORD_SJ, 'r', encoding='utf-8') as f:
        data = json.load(f)
    print(f"加载 word.sj: {len(data)} 条")

    if os.path.exists(OUT_DB):
        os.remove(OUT_DB)
    conn = sqlite3.connect(OUT_DB)
    conn.execute("PRAGMA journal_mode=OFF")
    conn.execute("PRAGMA synchronous=OFF")

    conn.execute("CREATE TABLE dict ("
                 "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                 "word TEXT NOT NULL,"
                 "norm TEXT NOT NULL,"
                 "pos TEXT,"
                 "meaning TEXT,"
                 "zh TEXT,"
                 "en TEXT,"
                 "stem TEXT)")
    conn.execute("CREATE UNIQUE INDEX idx_dict_norm ON dict(norm)")
    conn.execute("CREATE INDEX idx_dict_stem ON dict(stem)")

    conn.execute("CREATE TABLE dict_ngram (gram TEXT PRIMARY KEY, ids BLOB NOT NULL)")
    conn.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")

    def combine(zh, en, pos):
        primary = zh if zh.strip() else en
        return f"【{pos}】{primary}" if pos.strip() else primary

    rows = 0
    ngram_rows = 0
    seen = set()
    dedup = []
    for w, pos, en, zh in data:
        w = w.strip()
        if not w:
            continue
        n = normalize(w)
        if n in seen:
            continue
        seen.add(n)
        dedup.append((w, n, pos, combine(zh, en, pos), zh, en, stem(w)))
    conn.executemany(
        "INSERT INTO dict (word, norm, pos, meaning, zh, en, stem) VALUES (?,?,?,?,?,?,?)",
        dedup
    )
    rows = len(dedup)
    print(f"dict 表插入 {rows} 行（去重前 {len(data)} 条）")

    # 3-gram 倒排：聚合为 gram -> 紧凑 id 数组（4 字节大端定长）
    # 相比逗号分隔 norm 文本，可保留全部 gram（无高频丢弃退化），体积更小
    import struct
    gram_map = {}
    for row in conn.execute("SELECT id, norm FROM dict"):
        i, n = row
        if not n:
            continue
        for g in trigrams(n):
            gram_map.setdefault(g, []).append(i)
    gram_rows = []
    for g, ids in gram_map.items():
        ids.sort()
        gram_rows.append((g, b''.join(struct.pack('>I', i) for i in ids)))
    conn.executemany("INSERT INTO dict_ngram (gram, ids) VALUES (?,?)", gram_rows)
    ngram_rows = len(gram_rows)
    print(f"dict_ngram 表插入 {ngram_rows} 个唯一 gram，{len(gram_map.values())} 条 posting")

    conn.execute("INSERT OR REPLACE INTO meta VALUES ('version', '1')")
    conn.execute("INSERT OR REPLACE INTO meta VALUES ('entries', ?)", (str(rows),))
    conn.execute("INSERT OR REPLACE INTO meta VALUES ('ngrams', ?)", (str(ngram_rows),))
    conn.commit()
    conn.close()

    size = os.path.getsize(OUT_DB)
    print(f"完成: {OUT_DB}")
    print(f"大小: {size / 1024 / 1024:.1f} MB")


if __name__ == '__main__':
    build()
