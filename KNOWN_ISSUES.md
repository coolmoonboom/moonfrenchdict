# Known Issues & Feature Requests (已知问题与需求记录)

> 更新日期：2026-08-18
> 状态：问题已确认；功能需求待统一着手实现
> 参考截图：图1=查词界面、图2=动词分组界面、图3=句子分析界面
> OCR 方案已修复：本地 tesseract（chi_sim+fra+eng）可识别全部截图，界面细节已从截图提取并补充到各需求中

## 〇、最新用户反馈（2026-08-18 第三轮）

> 附 3 张新截图（narratable 查词无结果、plan 查词界面、plan + 联网释义）

### 反馈 1：查词界面单调且不准，中文覆盖不足

- 词库 8338 条中仅 815 条含中文释义，其余 7523 条（FreeDict 法英）只能显示英文，如 `plan` 显示 `【n.m.】1. map; 2. design, diagram, plan, plane` 无中文。
- 要求：
  - 有中文优先显示中文；无中文时自动联网翻译兜底，把中文翻译与英文释义并列显示（标注来源）。
  - 词性需突出展示（如 `n.m.` 徽章）。
  - 词根拆解：显示前缀 / 词根 / 后缀（如 planer → 词根 plan + 后缀 -er）。
  - 近似词、同词根词、派生词需要更丰富、更准确。
- 图1（narratable）：无精确匹配时，当前只有"未找到匹配"+联网释义，信息量少，需增加前缀建议、近似词、词根分析、联网兜底。

### 反馈 2：界面未接入安卓长按复制

- 整个 App（查词/变位/分组/句子分析/收藏）的文本都没有 `SelectionContainer`，无法长按选择复制。
- 要求：所有结果卡片文本支持长按复制（SelectionContainer 包裹）。

### 反馈 3：动词变位界面结构不符预期

- 当前 ConjugationScreen 只有"输入动词 → 变位表"，缺少：
  - 中文意义展示（本地词库 + 联网兜底）。
  - 单词拆解（前缀、词根、后缀 + 各自中文含义），位于中文意义下方。
  - 在单词拆解下方直接展示该动词所有变位：主动语态、被动语态、代动词语态下全部时态。
- 要求布局顺序：输入框 → 动词卡（原形 + 中文意义 + 助动词）→ 单词拆解卡 → 变位区（非人称形式 / 主动全时态 / 被动全时态 / 代动词全时态）。


## 一、已知问题（待修复）

### 问题 1：本地词库匹配会找到无关的词

- 描述：查词时模糊匹配（Levenshtein）会命中与查询词无关的词条，匹配准确度差。
- 影响：用户输入一个词，结果列表出现无关词，降低查词体验。

### 问题 2：在线翻译无法显示对应翻译

- 描述：百度翻译（fanyi.baidu.com/sug）结果无法在界面正常显示对应翻译。
- 影响：在线翻译功能基本不可用，用户看不到中文释义。

## 二、功能需求

### 需求 1：查词界面改进（图1）

- 描述：查词界面需要做到图1所示的效果（已从截图 OCR 提取布局）。
- 整体布局：
  - 顶部 Tab 栏：查词 / 动词变位 / 动词分组 / 句子分析 / 收藏
  - 搜索框输入单词（如 pommes）
  - 联网释义区（MyMemory 来源，标注"释义来自联网翻译，仅供参考；可将其添加到 word.js 存为本地词条"）
  - 疑似变体 / 相近单词区（点击查看），横排显示相近词卡片，每卡含：单词 + 词性 + 中文释义（如 `pomme  n.f. 苹果`、`homme  n.m. 男人`、`poème  n.m. 诗歌`）
- 用户查询单词后，界面显示：
  - 对应翻译（中文释义）
  - 词性
  - 近似词
  - 同词根词
  - 派生词

### 需求 2：动词变位功能（图2）

- 描述：可搜索动词并展示其变位，允许联网搜索和获取变位数据。
- 需要展示的变位内容：
  - 现在分词（participe présent）
  - 副动词（gérondif）
  - 复合不定式（infinitif passé）
  - 副动词过去式（gérondif passé）
  - 主动语态下的所有时态
  - 被动语态下的所有时态
  - 代动词语态下的所有时态
- 交互：用户搜索动词后，显示该动词在上述所有内容下的变位。

### 需求 2：动词变位功能（图2）

- 描述：可搜索动词并展示其变位，允许联网搜索和获取变位数据。
- 需要展示的变位内容：
  - 现在分词（participe présent）
  - 副动词（gérondif）
  - 复合不定式（infinitif passé）
  - 副动词过去式（gérondif passé）
  - 主动语态下的所有时态
  - 被动语态下的所有时态
  - 代动词语态下的所有时态
- 交互：用户搜索动词后，显示该动词在上述所有内容下的变位。

### 需求 3：动词分组功能（图2）

- 描述：法语动词分为三大分组，本功能针对第一组（-er）、第二组（-ir）和第三组（不规则）动词。
- 第一组和第二组动词需要展示以下时态下所有人称的变位：
  - 直陈现在（indicatif présent）
  - 未完成过去（imparfait）
  - 简单将来（futur simple）
  - 虚拟式现在（subjonctif présent）
  - 命令式（impératif）
- 第三组动词（不规则）：最复杂的一组，多为高频动词，需逐一记忆，可按词族归类（同族动词变位规律一致）。以下动词族必须支持所有人称变位：

#### 助动词
- être：是；存在
- avoir：有；拥有

#### aller 族
- aller：去；走
- s'en aller：离开

#### faire 族
- faire：做；制造
- refaire：重做

#### 情态动词类（pouvoir / vouloir / devoir / savoir）
- pouvoir：能够；可以
- vouloir：想要；希望
- devoir：必须；欠
- savoir：知道；会

#### venir 族
- venir：来；到来
- devenir：变成；成为
- revenir：回来；回复
- souvenir：记得；想起（se souvenir de）

#### tenir 族
- tenir：拿着；保持

#### prendre 族
- prendre：拿；取；乘坐
- comprendre：理解；包括
- apprendre：学习；得知

#### mettre 族
- mettre：放；穿；花费
- permettre：允许；准许
- promettre：许诺；保证

#### dire 族
- dire：说；告诉

#### lire 族
- lire：读；阅读

#### écrire 族
- écrire：写；书写
- décrire：描述；描写

#### voir 族
- voir：看见；看

#### recevoir 族
- recevoir：收到；接待
- apercevoir：察觉；瞥见

#### connaître 族
- connaître：认识；知道
- reconnaître：认出；承认
- paraître：出现；显得
- apparaître：出现；显现

#### naître 族（以 être 作助动词）
- naître：出生；诞生

#### vivre / survivre
- vivre：生活；活着
- survivre：幸存；活下来

#### suivre / poursuivre
- suivre：跟随；沿着
- poursuivre：继续；追捕

#### rire / sourire
- rire：笑
- sourire：微笑

#### boire / croire
- boire：喝；饮
- croire：相信；认为

#### -uire 族（conduire 型）
- conduire：驾驶；带领
- produire：生产；产生
- construire：建造；建立
- traduire：翻译
- réduire：减少；缩小
- détruire：摧毁；破坏

#### -aindre / -eindre / -oindre 族
- craindre：害怕；担心
- peindre：画；粉刷
- éteindre：熄灭；关（灯）
- joindre：连接；加上
- plaindre：同情；抱怨

#### vaincre
- vaincre：战胜；克服

#### courir / mourir / fuir
- courir：跑；奔跑
- mourir：死；去世
- fuir：逃跑；逃避

#### partir 族（-tir / -mir / -vrir）
- partir：离开；出发
- sortir：出去；拿出
- dormir：睡觉
- sentir：感觉；闻到
- servir：服务；有用
- mentir：撒谎

#### ouvrir 族
- ouvrir：打开
- offrir：赠送；提供
- souffrir：受苦；忍受
- couvrir：覆盖；遮盖
- découvrir：发现；揭开

#### -re 规则型（vendre 型）
- vendre：卖；出售
- perdre：失去；输
- attendre：等待
- entendre：听见；听懂
- répondre：回答；回复
- rendre：归还；使…成为
- descendre：下来；下降
- défendre：保卫；禁止
- rompre：折断；断绝

#### battre 族
- battre：打；敲

#### envoyer
- envoyer：发送；寄

#### valoir / pleuvoir / falloir
- valoir：价值；值得
- pleuvoir：下雨（无人称）
- falloir：必须；需要（无人称）

#### 代动词（pronominal）
- se laver：洗；盥洗
- se lever：起床；升起
- se coucher：躺下；睡觉
- se dépêcher：赶快
- se souvenir：记得；想起
- se sentir：感到；觉得
- se taire：闭嘴；保持沉默
- s'appeler：名叫；自称

### 需求 4：句子分析功能（图3）

- 描述：用户输入句子并点击翻译后，在此界面显示分析结果（已从截图 OCR 提取布局）。
- 界面布局：
  - 顶部 Tab 栏包含"句子分析"入口
  - 句子输入框（如 `J'ai mangé une pomme dans la cuisine`）
  - "试试示例"按钮
  - 整句翻译区（来源 MyMemory 联网，如"我在厨房吃了一个苹果"）
  - 词数统计（如"共8个词 · 名词2 动词2 介词1 代词1"）
- 逐词列表，每个词条卡片显示：
  - 词形 + 词性 + 句子成分
  - 缩写拆解（如 `J'ai` → `je + ai` 省音）
  - 原形（如 avoir、manger、pomme），未收录时提示"本地词库未收录，尝试在线查询…"
  - 数（单数/复数）
  - 动词额外显示：时态（如复合过去时）、人称、语态、助动词、变位/过去分词说明、构造说明（如"直陈式现在时 + 过去分词"）
  - 近义词（如 posséder）
  - 同根词族（如 pomme / pomme de terre）
  - 快捷查词按钮
- 每单词需显示：
  - 词性（partie du discours）
  - 句子成分（语法分析，如主语/谓语/介词宾语/其他）
  - 动词原形（动词的 infinitive）
- 交互能力：
  - 显示的每个词都支持快捷查词
  - 对存在同词根的词，可以显示同词根词

## 三、整体交互与数据说明

- 全部界面共用顶部 Tab 栏：查词 / 动词变位 / 动词分组 / 句子分析 / 收藏
- 联网释义与整句翻译均使用 MyMemory 接口（图1、图3 均显示 MyMemory 来源），需将在线结果可"存为本地词条"
- 词条卡片统一展示：词形、词性（缩写）、中文释义、相关词（近义/同根/派生）
