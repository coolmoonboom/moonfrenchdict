#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建背单词/背动词数据资产。

数据来源：
- android/app/src/main/assets/dictionary.db （本地词典，word/pos/zh/en）
- tools/data/fr_full.txt （全量法语词频表，仅用于推导 CEFR/专八分级；缺失时退回 fr_freq_50k.txt）
  词频表来源：https://github.com/hermitdave/FrequencyWords (CC-BY-SA)

分级策略：
- 动词与非动词各自按词频从高频到低频排序；
- 按累计名额切分到 A1/A2/B1/B2/TFS4/TFS8（见 VERB_CUM / WORD_CUM）；
- 未命中词频表或超出专八名额的词归入「高级法语」。

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
# 优先使用全量词频表（覆盖更广，保证分级名额能填满），缺失时退回 50k 版本
FREQ_PATH = os.path.join(ROOT, "tools/data/fr_full.txt")
FREQ_FALLBACK = os.path.join(ROOT, "tools/data/fr_freq_50k.txt")
OUT_PATH = os.path.join(ROOT, "android/app/src/main/assets/vocab/vocab.json")

# ---------------------------------------------------------------- 分级
# 依据用户提供的 CEFR/专业大纲分级表（累计词汇量）：
#   等级, 累计动词原形, 非动词词汇, 总词汇
#   A1            100        400        ~500
#   A2            180        820        ~1000
#   B1            450        2050       ~2500
#   B2            900        4100       ~5000
#   专四 TFS4     1100       5900       ~7000
#   专八 TFS8     1500       9500       ~11000
# 超出专八的剩余词统一进入「高级法语」。
#
# 动词累计沿用用户 CSV（真实动词能力分级）；非动词累计 = 总词汇累计 − 动词累计，
# 使各等级总词数呈现 A1≈500 → A2≈1000 → B1≈2500 → B2≈5000 → TFS4≈7000 → TFS8≈11000
# 的平滑递增阶梯，避免单个等级堆入过多词。
#
# 动词与非动词各自按词频从高频到低频排序后按累计名额切分；未命中词频表的词
# 视为低频，直接落入「高级法语」。
LEVELS = [
    ("A1", "A1"),
    ("A2", "A2"),
    ("B1", "B1"),
    ("B2", "B2"),
    ("TFS4", "专四 TFS4"),
    ("TFS8", "专八 TFS8"),
    ("ADV", "高级法语"),
]
# (等级 id, 该等级累计词数上限)
VERB_CUM = [("A1", 100), ("A2", 180), ("B1", 450),
            ("B2", 900), ("TFS4", 1100), ("TFS8", 1500)]
WORD_CUM = [("A1", 400), ("A2", 820), ("B1", 2050),
            ("B2", 4100), ("TFS4", 5900), ("TFS8", 9500)]
ADV_LEVEL = "ADV"

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
# 动变元语言释义（source 词典把 devoirs/va 等变位形式标成名词，释义为「…第X人称…现在式」）
META = re.compile(r'第?[一二三1-3]?人[称成](?:单|复)?数|现在式|愈过去|先过去|简单过去|未完成过去|变位式')


def _strip_noise(zh: str) -> str:
    s = PAREN.sub('', zh)
    s = BRACKET.sub('', s)
    s = ANGLE.sub('', s)
    s = CIRC.sub(' ', s)
    s = POS_PREFIX.sub('', s)
    s = LEAD_MARK.sub('', s)
    s = MID_MARK.sub(' ', s)
    s = POS_TOK.sub(' ', s)
    # 「…阴森森的2.阴森的」义项编号紧贴中文尾部 -> 转为分隔
    s = re.sub(r'(?<=[\u4e00-\u9fff。])\s*\d+[.．]\s*', '，', s)
    # 中文与拉丁词直接粘连（「解剖病理学anatomopathologist」）-> 切开便于段级清洗
    s = re.sub(r'(?<=[\u4e00-\u9fff])(?=[a-zA-Zà-öø-ÿ]{2,})', ' ', s)
    s = re.sub(r'([a-zA-Zà-öø-ÿ]{2,})[.,.;，。]?\s*(?=[\u4e00-\u9fff])', r'\1 ', s)
    return re.sub(r'\s+', ' ', s).strip(' ,;，；、:：。.')


def _clean_segment(p: str) -> str:
    """单个逗号级片段：去掉开头的「的/地/得」残缺、词组示例段、英文段；返回 '' 表示弃。"""
    s = p.strip(' ,.。:：')
    # 「到，的第一人称…」这类以结构助词开头的碎段
    while s and s[0] in '的地得':
        s = s[1:].lstrip()
    s = s.strip(' ,.。:：')
    if not s:
        return ''
    # 词组/示例段（se ~ / de ~/ pl. ~x 等）
    if '~' in s:
        return ''
    # 纯拉丁段（同义英文/衍生同形词，如 anatomopathologist）
    if not CJK.search(s) and re.search(r'[a-zA-Zà-öø-ÿœæ]{4,}', s):
        return ''
    return s


def _fmt_meaning(segs):
    seen, uniq = set(), []
    for p in segs:
        if p and p not in seen:
            seen.add(p)
            uniq.append(p)
    out, n = '', 0
    for p in uniq:
        if n >= 2:
            break  # 简注风格：最多两个片段
        cand = p if not out else out + '，' + p
        if len(cand) > 30:
            break  # 只在片段边界截断，不拦腰切词
        out = cand
        n += 1
    return out[:30]


def _sense_ok(s: str) -> bool:
    if not s or '…' in s:
        return False
    if len(CJK.findall(s)) == 0:
        return False
    if META.search(s):
        return False
    return True


def clean_meaning(zh: str) -> str:
    """按 分号义项 > 逗号片段 分层择优：跳过省略号/元语言/词组示例/残缺段，
    首义过简（单个汉字）时并入第二义项补充信息；输出最多两段的简注。"""
    if not zh:
        return ""
    senses = []
    for raw in re.split(r'[;；]', _strip_noise(zh)):
        segs = []
        for p in re.split(r'[,，、:：\s]+', raw):
            c = _clean_segment(p)
            if c and not META.search(c):
                segs.append(c)
        if not segs:
            continue
        s = '，'.join(dict.fromkeys(segs))
        if _sense_ok(s) and s not in senses:
            senses.append(s)
    if not senses:
        return ""
    if len(CJK.findall(senses[0])) <= 1 and len(senses) > 1:
        return _fmt_meaning(senses[0].split('，') + senses[1].split('，'))
    return _fmt_meaning(senses[0].split('，'))


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


def load_freq(path):
    freq = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.rsplit(' ', 1)
            if len(parts) != 2:
                continue
            try:
                key = norm(parts[0])
                cnt = int(parts[1])
            except ValueError:
                continue
            if key:
                freq[key] = freq.get(key, 0) + cnt
    ordered = sorted(freq.items(), key=lambda kv: -kv[1])
    return {w: i for i, (w, _) in enumerate(ordered, 1)}


def assign_levels(candidates, cum):
    """candidates 已按词频从高频到低频排序；按累计名额给出等级，超出部分归 ADV。"""
    out = {}
    for i, w in enumerate(candidates):
        lvl = ADV_LEVEL
        for lid, hi in cum:
            if i < hi:
                lvl = lid
                break
        out[w] = lvl
    return out


def main():
    if not os.path.exists(DB_PATH):
        print("缺少词典数据库:", DB_PATH, file=sys.stderr)
        return 1
    freq_path = FREQ_PATH if os.path.exists(FREQ_PATH) else FREQ_FALLBACK
    if not os.path.exists(freq_path):
        print("缺少词频表:", FREQ_PATH, file=sys.stderr)
        return 1
    print("词频表:", freq_path)

    rank = load_freq(freq_path)
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
            # 变位元语言条目混入（devons=「devoir 的第一人称复数现在式 nous devons」）：
            # 释义含变位描述且原样出现本词才剔除，避免误删含短语/术语的正常词条
            if zh and len(w) >= 3 and META.search(zh.lower()) and re.search(
                    r"(?<![a-zà-öø-ÿ'])" + re.escape(w) + r"(?![a-zà-öø-ÿ'])",
                    zh.lower()):
                continue
            good = valid_meaning(clean_meaning(zh))
            prev = out.get(w)
            if prev is None or (not valid_meaning(clean_meaning(prev[1])) and good):
                out[w] = (pos, zh)
        return out

    verb_rows = query_words(VERB_TAGS, ending_filter=True)
    noun_rows = query_words(NONVERB_TAGS)

    stats = {"verb": 0, "word": 0, "bad": 0, "nofreq": 0}

    def prep(rows):
        """清洗释义（不合格丢弃），返回 {word: (pos, meaning)}。"""
        out = {}
        for w, (pos, zh) in rows.items():
            m = clean_meaning(zh)
            if not valid_meaning(m):
                stats["bad"] += 1
                continue
            if norm(w) not in rank:
                stats["nofreq"] += 1
            out[w] = (pos, m)
        return out

    def ordered(rows):
        """从易到难：命中词频的按频率从高到低；未收录词频的按长度再字母序。"""
        known = sorted((w for w in rows if norm(w) in rank),
                       key=lambda w: rank[norm(w)])
        unknown = sorted((w for w in rows if norm(w) not in rank),
                         key=lambda w: (len(w), w))
        return known + unknown

    verb_all = prep(verb_rows)
    word_all = prep(noun_rows)
    verb_level = assign_levels(ordered(verb_all), VERB_CUM)
    word_level = assign_levels(ordered(word_all), WORD_CUM)

    words = []
    for w, (pos, m) in verb_all.items():
        words.append((w, pos, verb_level[w], m, 1))
        stats["verb"] += 1
    for w, (pos, m) in word_all.items():
        words.append((w, pos, word_level[w], m, 0))
        stats["word"] += 1

    level_order = {lid: i for i, (lid, _) in enumerate(LEVELS)}
    words.sort(key=lambda x: (level_order.get(x[2], 99), x[0]))
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
    print("--- 分级明细 (动词 / 非动词) ---")
    cum_v = cum_w = 0
    for lid, label in LEVELS:
        v = sum(1 for w in words if w[2] == lid and w[4] == 1)
        n = sum(1 for w in words if w[2] == lid and w[4] == 0)
        cum_v += v
        cum_w += n
        print("  %-6s 动词 %5d (累计 %5d) · 非动词 %5d (累计 %5d) · 总 %5d"
              % (lid, v, cum_v, n, cum_w, v + n))
    print("跳过(释义不合格):", stats["bad"], " (其中无词频):", stats["nofreq"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
