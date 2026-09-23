#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建背词本地例句资产（不依赖任何在线 AI）。

数据来源：
- tools/data/tatoeba/fra_sentences.tsv.bz2   （Tatoeba 法语句子，CC-BY 2.0 FR）
- tools/data/tatoeba/cmn_sentences.tsv.bz2   （Tatoeba 中文句子，CC-BY 2.0 FR）
- tools/data/tatoeba/fra-cmn_links.tsv.bz2   （法汉句对链接）
- android/app/src/main/assets/dictionary.db  （取动词词干，辅助变位匹配）
- android/app/src/main/assets/vocab/vocab.json（目标词表）

产物：
- android/app/src/main/assets/vocab/examples.json
  结构：{"version":1, "source":..., "examples":{"词":[法语例句, 中文翻译], ...}}

用法：
    python3 tools/build_vocab_examples.py
"""
import bz2
import json
import os
import re
import sqlite3
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TATOEBA = os.path.join(ROOT, "tools/data/tatoeba")
FRA_PATH = os.path.join(TATOEBA, "fra_sentences.tsv.bz2")
CMN_PATH = os.path.join(TATOEBA, "cmn_sentences.tsv.bz2")
LINKS_PATH = os.path.join(TATOEBA, "fra-cmn_links.tsv.bz2")
DB_PATH = os.path.join(ROOT, "android/app/src/main/assets/dictionary.db")
VOCAB_PATH = os.path.join(ROOT, "android/app/src/main/assets/vocab/vocab.json")
OUT_PATH = os.path.join(ROOT, "android/app/src/main/assets/vocab/examples.json")

SOURCE = "Tatoeba (CC-BY 2.0 FR) https://tatoeba.org"
MIN_FR, MAX_FR = 10, 90
MAX_ZH = 40
INFINITIVE_END = ("er", "ir", "re", "oir")


def norm(s: str) -> str:
    s = (s or "").lower().strip()
    s = unicodedata.normalize("NFD", s)
    return "".join(ch for ch in s if unicodedata.category(ch) != "Mn")


SPLIT = re.compile(r"[^a-z0-9]+")


def tokens(text: str):
    """归一化并切词：撇号/连字符都作为分隔符。"""
    return [t for t in SPLIT.split(norm(text)) if t]


def load_sentences(path):
    out = {}
    with bz2.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                out[parts[0]] = parts[2]
    return out


def load_links(path):
    links = {}
    with bz2.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 2:
                links.setdefault(parts[0], []).append(parts[1])
    return links


def verb_stem(word: str, stem_map: dict) -> str:
    s = stem_map.get(word)
    if s and 3 <= len(s) < len(word):
        return s
    for end in INFINITIVE_END:
        if word.endswith(end) and len(word) - len(end) >= 3:
            return word[: -len(end)]
    return word


def main():
    for p in (FRA_PATH, CMN_PATH, LINKS_PATH, DB_PATH, VOCAB_PATH):
        if not os.path.exists(p):
            print("缺少输入:", p, file=sys.stderr)
            return 1

    print("读取目标词表…")
    with open(VOCAB_PATH, encoding="utf-8") as f:
        vocab = json.load(f)
    words = [(w[0], bool(w[4])) for w in vocab["words"]]

    stem_map = {}
    db = sqlite3.connect(DB_PATH)
    for word, stem in db.execute("SELECT word, stem FROM dict WHERE stem IS NOT NULL"):
        stem_map.setdefault((word or "").lower().strip(), (stem or "").lower().strip())

    print("读取 Tatoeba 句对…")
    fra = load_sentences(FRA_PATH)
    cmn = load_sentences(CMN_PATH)
    links = load_links(LINKS_PATH)

    # 只保留同时有中法文、且法语长度合适的句对
    pairs = []  # (fra_id, fr, zh)
    for fid, cids in links.items():
        fr = fra.get(fid)
        if not fr or not (MIN_FR <= len(fr) <= MAX_FR):
            continue
        zh = next((cmn[c] for c in cids if cmn.get(c) and len(cmn[c]) <= MAX_ZH), None)
        if not zh:
            continue
        pairs.append((fid, fr, zh))
    print("可用法汉句对:", len(pairs))

    # 倒排：归一化 token -> 句对下标
    index = {}
    pair_tokens = []
    for i, (fid, fr, zh) in enumerate(pairs):
        toks = tokens(fr)
        pair_tokens.append(toks)
        for t in set(toks):
            index.setdefault(t, []).append(i)

    print("为目标词匹配例句…")
    # 先处理候选少的词，避免冷门词的好句子被高频词先占走
    order = sorted(range(len(words)), key=lambda i: _cand_count(words[i], index))
    used = set()
    result = {}
    matched = 0
    for i in order:
        word, is_verb = words[i]
        stem = verb_stem(word, stem_map) if is_verb else None
        cands = _candidates(word, stem, index, pair_tokens)
        if not cands:
            continue
        # 先挑未被占用的最短句，退而求其次允许复用
        pick = None
        for ci in sorted(cands, key=lambda j: (len(pairs[j][1]), j)):
            if ci not in used:
                pick = ci
                break
        if pick is None:
            pick = min(cands, key=lambda j: (len(pairs[j][1]), j))
        used.add(pick)
        _, fr, zh = pairs[pick]
        result[word] = [fr, zh]
        matched += 1

    out = {"version": 1, "source": SOURCE, "examples": result}
    tmp = OUT_PATH + ".tmp"
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, OUT_PATH)
    print("写入:", OUT_PATH, "大小: %.1f KB" % (os.path.getsize(OUT_PATH) / 1024))
    print("命中例句的词:", matched, "/", len(words))
    return 0


def _match_key(word: str, is_verb: bool, stem: str):
    """返回用于精确倒排匹配的 token 列表。"""
    t = norm(word)
    keys = [t]
    if not is_verb:
        keys += [t + "s", t + "x"]
    return keys


def _cand_count(word_info, index):
    word, is_verb = word_info
    keys = _match_key(word, is_verb, None)
    ids = set()
    for k in keys:
        ids.update(index.get(k, ()))
    return len(ids)


def _candidates(word, stem, index, pair_tokens):
    keys = _match_key(word, True if stem else False, stem)
    ids = set()
    for k in keys:
        ids.update(index.get(k, ()))
    # 动词：用词干前缀匹配变位（词干长度 >= 4 才启用，避免误命中）
    if stem and len(stem) >= 4:
        pref = set()
        for t, js in index.items():
            if t != norm(word) and t.startswith(stem):
                pref.update(js)
        ids |= pref
    return ids


if __name__ == "__main__":
    sys.exit(main())
