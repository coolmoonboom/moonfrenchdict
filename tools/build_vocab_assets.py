#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建背单词/背动词数据资产。

数据来源：
- android/app/src/main/assets/dictionary.db （本地词典，word/pos/zh/en）
- tools/data/fr_freq_50k.txt （法语词频表，仅用于推导 CEFR 分级）
  词频表来源：https://github.com/hermitdave/FrequencyWords (CC-BY-SA)

产物：
- android/app/src/main/assets/vocab/vocab.json

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

# ---------------------------------------------------------------- 分级阈值
LEVEL_RANKS = [
    ("A1", 900),
    ("A2", 2200),
    ("B1", 5000),
    ("B2", 10000),
    ("C1", 20000),
    ("C2", 50000),
]

# ---------------------------------------------------------------- 主题定义
# 关键词均为多字词，避免单字误判；在【清洗后的简义】中做子串匹配。
THEMES = [
    ("restaurant", "餐厅点餐", "Au restaurant",
     ["餐厅", "饭店", "餐馆", "菜单", "烹饪", "厨师", "厨房", "食物", "饮料", "吃饭",
      "菜肴", "味道", "咖啡馆", "酒吧", "美食", "餐饮", "点餐", "甜品", "餐具", "饥饿",
      "口渴", "汤", "菜肴"]),
    ("food", "食材食物", "Les aliments",
     ["面包", "水果", "蔬菜", "牛奶", "鸡蛋", "稻米", "白糖", "食盐", "油脂", "咖啡",
      "茶叶", "巧克力", "甜点", "蛋糕", "奶酪", "香肠", "粮食", "肉类", "牛肉", "猪肉",
      "鸡肉", "美食"]),
    ("education", "教育学习", "L'éducation",
     ["学校", "学习", "学生", "教师", "老师", "教育", "考试", "课程", "课堂", "大学",
      "中学", "小学", "知识", "练习", "作业", "学位", "教学", "讲授", "阅读", "写作",
      "毕业", "年级", "书籍"]),
    ("shopping", "商店购物", "Les courses",
     ["商店", "购物", "商品", "价格", "付款", "交易", "顾客", "市场", "便宜", "昂贵",
      "超市", "售货", "收银", "打折", "钞票", "金钱", "购买", "出售", "费用", "货物"]),
    ("transport", "交通出行", "Les transports",
     ["汽车", "火车", "飞机", "公交", "马路", "车站", "车票", "驾驶", "机场", "铁路",
      "地铁", "自行车", "摩托", "轮船", "交通", "出发", "到达", "旅行", "航班", "道路",
      "乘坐", "车辆"]),
    ("home", "家居住所", "La maison",
     ["房子", "住宅", "公寓", "房间", "厨房", "卧室", "家具", "门窗", "桌子", "椅子",
      "床铺", "灯光", "墙壁", "屋顶", "楼层", "花园", "楼梯", "客厅", "沙发", "居住"]),
    ("family", "家庭亲友", "La famille",
     ["父亲", "母亲", "父母", "儿子", "女儿", "兄弟", "姐妹", "爷爷", "奶奶", "叔叔",
      "阿姨", "亲戚", "家庭", "丈夫", "妻子", "孩子", "婚姻", "怀孕", "亲属", "家人"]),
    ("body", "身体医疗", "Le corps et la santé",
     ["身体", "头发", "眼睛", "耳朵", "嘴巴", "鼻子", "心脏", "血液", "骨头", "疾病",
      "疼痛", "医生", "药物", "医院", "健康", "治疗", "病人", "伤口", "肌肉", "皮肤",
      "呼吸", "器官"]),
    ("work", "工作职业", "Le travail",
     ["工作", "职业", "老板", "同事", "公司", "办公", "雇佣", "工厂", "行业", "会议",
      "工资", "招聘", "简历", "商务", "生意", "职员", "失业", "合同", "项目", "任务"]),
    ("time", "时间日期", "Le temps",
     ["时间", "小时", "分钟", "秒钟", "年份", "月份", "星期", "今天", "明天", "昨天",
      "早上", "晚上", "春天", "夏天", "秋天", "冬天", "季节", "世纪", "期间", "假期",
      "日期", "时刻", "时代"]),
    ("nature", "天气自然", "La nature",
     ["天气", "下雨", "雪花", "大风", "云彩", "太阳", "月亮", "星星", "大海", "山脉",
      "河流", "树木", "花草", "森林", "温度", "寒冷", "炎热", "气候", "闪电", "暴风雨",
      "自然", "空气", "火焰", "海洋"]),
    ("travel", "旅行住宿", "Le voyage",
     ["旅行", "旅游", "酒店", "旅馆", "预订", "度假", "导游", "参观", "护照", "行李",
      "景点", "游客", "海滩", "机票", "远足", "住宿", "观光", "野营"]),
    ("clothing", "衣着服饰", "Les vêtements",
     ["衣服", "裤子", "裙子", "鞋子", "帽子", "外套", "大衣", "衬衫", "布料", "口袋",
      "服饰", "时尚", "尺寸", "手套", "围巾", "纽扣", "穿着", "连衣裙", "西装"]),
    ("city", "城市地点", "La ville",
     ["城市", "街道", "广场", "公园", "银行", "邮局", "桥梁", "村庄", "小镇", "地区",
      "建筑", "地址", "郊区", "首都", "教堂", "市区", "社区", "路口", "大道"]),
    ("sport", "运动娱乐", "Le sport et les loisirs",
     ["运动", "比赛", "跑步", "游泳", "游戏", "跳舞", "歌唱", "乐器", "运动场", "球队",
      "球员", "冠军", "玩具", "钓鱼", "足球", "篮球", "网球", "滑雪", "健身", "锻炼",
      "娱乐", "唱歌"]),
    ("emotion", "情感性格", "Les émotions",
     ["高兴", "悲伤", "生气", "害怕", "喜爱", "厌恶", "幸福", "痛苦", "惊讶", "担心",
      "性格", "勇敢", "温柔", "骄傲", "害羞", "嫉妒", "希望", "绝望", "情感", "情绪",
      "快乐", "愤怒", "恐惧"]),
    ("communication", "通讯传媒", "La communication",
     ["电话", "手机", "电脑", "网络", "邮件", "信息", "新闻", "电视", "广播", "报纸",
      "媒体", "短信", "消息", "屏幕", "键盘", "互联网", "通信", "通讯", "节目", "频道"]),
    ("art", "艺术文化", "L'art et la culture",
     ["艺术", "绘画", "文学", "诗歌", "戏剧", "博物馆", "文化", "历史", "传统", "雕塑",
      "画家", "作家", "小说", "音乐", "电影", "展览", "剧院", "音乐会", "作品", "演员"]),
    ("society", "社会政治", "La société",
     ["社会", "政府", "法律", "国家", "政治", "选举", "总统", "权利", "公民", "警察",
      "犯罪", "法院", "战争", "和平", "税收", "军队", "国王", "制度", "民主", "议员",
      "义务", "条约"]),
    ("science", "科学数学", "Les sciences",
     ["科学", "数学", "物理", "化学", "生物", "数字", "计算", "研究", "实验", "理论",
      "技术", "工程", "数据", "统计", "几何", "测量", "机器", "方程", "单位", "仪器"]),
    ("animal", "动物植物", "Les animaux et les plantes",
     ["动物", "鸟类", "鱼类", "昆虫", "植物", "野兽", "宠物", "饲养", "花朵", "叶子",
      "牲口", "家禽", "哺乳动物", "树木", "森林"]),
    ("politeness", "礼仪社交", "La politesse",
     ["你好", "谢谢", "对不起", "再见", "问候", "礼貌", "招呼", "欢迎", "祝贺", "邀请",
      "礼物", "道歉", "感谢", "祝福", "客气", "致敬", "告别", "介绍"]),
    ("quantity", "数字量度", "Les nombres et mesures",
     ["数字", "数量", "长度", "重量", "公里", "公斤", "一半", "双倍", "分数", "百分比",
      "计量", "尺寸", "成千", "度量", "数目", "计算"]),
    ("geography", "地理世界", "La géographie",
     ["国家", "大陆", "海洋", "地区", "边界", "岛屿", "沙漠", "地图", "世界", "地球",
      "山脉", "领土", "火山", "气候", "半球", "经纬"]),
]

CJK = re.compile(r'[\u4e00-\u9fff]')

# 补充关键词（按主题追加，覆盖词典简义中的常见同义词）
_EXTRA_KEYWORDS = {
    "restaurant": ["招待", "宴请", "品尝", "餐桌", "预约"],
    "food": ["饮食", "菜肴", "口味", "食品", "副食"],
    "education": ["培养", "训练", "智力", "学期", "教材", "学子"],
    "shopping": ["消费", "廉价", "收费", "进货", "百货"],
    "transport": ["运输", "行驶", "路程", "航线", "通行", "车费"],
    "home": ["住房", "居所", "房屋", "楼层", "宅"],
    "family": ["养育", "亲属", "姻亲", "配偶"],
    "body": ["医疗", "体格", "患病", "卫生", "内脏", "脉搏"],
    "work": ["职务", "劳动", "工人", "经理", "企业", "器材"],
    "time": ["时期", "上午", "下午", "夜间", "钟点", "年限"],
    "nature": ["阳光", "狂风", "雷雨", "天空", "陆地", "洪水", "季节"],
    "travel": ["旅程", "宾馆", "游览", "远行", "旅客", "旅程"],
    "clothing": ["时装", "鞋袜", "衣料", "装扮", "布料"],
    "city": ["城区", "乡村", "城镇", "楼房", "公路", "街市"],
    "sport": ["竞赛", "竞技", "棋类", "球赛", "健身", "游艺"],
    "emotion": ["感激", "失望", "满意", "热心", "忧愁", "畏惧", "欢喜"],
    "communication": ["电信", "讯息", "报道", "录音", "信息", "电话"],
    "art": ["美术", "乐曲", "影视", "艺术品", "绘画", "写作"],
    "society": ["政法", "政权", "刑法", "民法", "国防", "行政"],
    "science": ["科技", "科研", "数据", "仪器", "数学", "化学"],
    "animal": ["家畜", "野生动物", "花草", "鸟类", "植物"],
    "politeness": ["庆贺", "礼节", "客套", "辞别", "礼节"],
    "quantity": ["数额", "比例", "倍数", "计量"],
    "geography": ["地形", "疆域", "省份", "国土", "海域"],
}
THEMES = [
    (tid, zh, fr, kws + _EXTRA_KEYWORDS.get(tid, []))
    for tid, zh, fr, kws in THEMES
]


def norm(s: str) -> str:
    s = s.lower().strip()
    s = unicodedata.normalize('NFD', s)
    return ''.join(ch for ch in s if unicodedata.category(ch) != 'Mn')


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


def theme_text(zh: str) -> str:
    """用于主题匹配的文本：去掉噪声后的首个语义分句（保留全部义项）。"""
    if not zh:
        return ""
    return re.split(r'[;；]', _strip_noise(zh))[0]


def valid_meaning(m: str, rank: int) -> bool:
    n = len(CJK.findall(m))
    if n == 0:
        return False
    if n == 1 and rank > 5000:
        return False
    if '~' in m:
        return False
    if m.startswith('的'):
        return False
    return True


WORD_RE = re.compile(r"^[a-zà-öø-ÿœæ][a-zà-öø-ÿœæ'\-]{1,19}$")
VERB_TAGS = ('v.', 'v.1', 'v.2', 'v.3', 'v.t.', 'v.i.', 'v.pr.', 'v.impers.',
             'v.pron.', 'v.aux.', 'v.intr.', 'v.tr.')
NONVERB_TAGS = ('n.', 'n.m.', 'n.f.', 'n.m.pl', 'n.f.pl', 'n.m.&adj.', 'n.f.&adj.',
                'adj.', 'adj', 'a.', 'adv.', 'adv', 'noun', 'num', 'num.', 'intj',
                'intj.', 'pron.', 'pron', 'prep.', 'prep', 'conj.', 'conj')


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


def level_of(rank: int) -> str:
    for lvl, hi in LEVEL_RANKS:
        if rank <= hi:
            return lvl
    return "C2"


def themes_of(meaning: str) -> list:
    out = []
    for tid, _zh, _fr, kws in THEMES:
        for kw in kws:
            if kw in meaning:
                out.append(tid)
                break
    return out


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
    accept = set(NONVERB_TAGS) | set(VERB_TAGS)
    rows = cur.execute(
        "SELECT word, pos, zh, en FROM dict WHERE pos IN (%s)" %
        ",".join("?" * len(accept)), tuple(accept)).fetchall()

    seen = set()
    words = []
    skipped_bad = 0
    skipped_nofreq = 0
    for word, pos, zh, en in rows:
        w = (word or '').strip()
        if not WORD_RE.match(w):
            continue
        if w in seen:
            continue
        r = rank.get(norm(w))
        if r is None:
            skipped_nofreq += 1
            continue
        m = clean_meaning(zh)
        if not valid_meaning(m, r):
            skipped_bad += 1
            continue
        seen.add(w)
        words.append((w, pos, level_of(r), themes_of(theme_text(zh)), m,
                      1 if pos.lower().startswith('v.') else 0))

    words.sort(key=lambda x: (x[2], x[0]))
    out = {
        "version": 1,
        "generated": date.today().isoformat(),
        "themes": [[tid, zh, fr] for tid, zh, fr, _ in THEMES],
        "words": [[w, p, lvl, ",".join(th), z, v] for w, p, lvl, th, z, v in words],
    }
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    tmp = OUT_PATH + ".tmp"
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    os.replace(tmp, OUT_PATH)

    from collections import Counter
    by_level = Counter(w[2] for w in words)
    by_kind = Counter("verb" if w[5] else "word" for w in words)
    themed = sum(1 for w in words if w[3])
    print("写入:", OUT_PATH)
    print("总词数:", len(words), "大小: %.1f KB" % (os.path.getsize(OUT_PATH) / 1024))
    print("分级:", dict(by_level))
    print("词/动词:", dict(by_kind))
    print("带主题词数:", themed, "无主题:", len(words) - themed)
    per_theme = Counter(t for w in words for t in w[3])
    print("各主题:", dict(per_theme))
    print("跳过(释义不合格):", skipped_bad, " 跳过(不在词频表):", skipped_nofreq)
    return 0


if __name__ == "__main__":
    sys.exit(main())
