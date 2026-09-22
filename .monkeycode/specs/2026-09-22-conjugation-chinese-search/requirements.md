# Requirements Document

## Introduction

在「变位」功能页新增中文查询能力。中文输入支持单个动词词/词头（如「喜欢」「吃」）与短语/短句（如「我喜欢你」「去散步」）两种粒度。系统先给出含义相同或相近的若干候选法语动词；用户点选目标动词后，系统展示该动词的完整变位内容。现有输入法语原形与变体的查询行为保持不变。

## Glossary

- **变位查询模块（Conjugation Lookup Module）**：变位页中解析用户输入并展示变位结果的组件（对应 `ConjugationScreen`）。
- **中文查询（Chinese Query）**：至少包含一个 CJK 统一表意文字（U+4E00–U+9FFF）的输入串。
- **中文词查询（Chinese Lemma Query）**：仅含单个动词词或词头的中文查询，例如「喜欢」「吃」。
- **中文短语查询（Chinese Phrase Query）**：包含多个词或成句的中文查询，例如「我喜欢你」「去散步」。
- **核心动词（Core Verb Meaning）**：从中文短语查询中提取出的主要动作含义，用于生成候选动词。
- **法语查询（French Query）**：不含 CJK 统一表意文字的输入串。
- **候选动词（Candidate Verb）**：一个法语动词不定式，其含义与中文查询相同或相近。
- **变位结果（Conjugation Result）**：某动词的主动、被动、代词式各时态变位、读音分组、句型用法、构词拆解与释义（对应现有 `Conjugation` 展示内容）。
- **本地词典（Local Dictionary）**：`assets/dictionary.db` 中 `dict` 表的数据。
- **AI 模型（AI Model）**：用户在 AI 设置中配置的大语言模型（对应 `AIPreferences.modelConfig`）。
- **变位引擎（Conjugation Engine）**：`VerbConjugator`，判定并生成法语动词变位。

## Requirements

### Requirement 1 中文查询识别与模式切换

**User Story:** AS 法语学习者, I want 在变位页直接输入中文, so that 我不必先知道对应法语动词即可查其变位。

#### Acceptance Criteria

1. WHEN 变位页的输入包含至少一个 CJK 统一表意文字, 变位查询模块 SHALL 进入中文候选模式并展示候选动词列表。
2. WHEN 变位页的输入不含 CJK 统一表意文字, 变位查询模块 SHALL 沿用现有法语原形与变体查询路径。
3. WHILE 输入为空或仅含空白字符, 变位查询模块 SHALL 清空候选列表与变位结果并展示空白状态。
4. WHEN 中文查询为中文词查询, 变位查询模块 SHALL 依据该词的含义生成候选动词。
5. WHEN 中文查询为中文短语查询, 变位查询模块 SHALL 提取其中的核心动词含义并据此生成候选动词。
6. WHEN 中文查询为中文短语查询且已配置 AI 模型, 变位查询模块 SHALL 使用 AI 模型提取核心动词含义。
7. WHEN 中文查询为中文短语查询, 变位查询模块 SHALL 在候选区域展示识别出的核心动词含义。

### Requirement 2 候选动词生成

**User Story:** AS 用户, I want 看到含义相同或相近的多个法语动词, so that 我能从中挑出想查的词。

#### Acceptance Criteria

1. WHEN 中文查询非空, 变位查询模块 SHALL 返回至多 8 个候选动词。
2. 变位查询模块 SHALL 为每个候选动词展示法语不定式、词性、中文释义、IPA 音标与一条简短例句。
3. 变位查询模块 SHALL 按候选动词与中文查询的语义相关度降序排列候选。
4. WHEN 候选来自 AI 模型, 变位查询模块 SHALL 在候选上展示 AI 模型生成的例句。
5. WHILE AI 模型未配置, 变位查询模块 SHALL 展示候选的不定式、词性、中文释义与 IPA 音标。
6. 变位查询模块 SHALL 对候选去重, 使同一不定式仅出现一次。
7. WHEN 本地词典中存在与中文查询匹配的动词, 变位查询模块 SHALL 将其纳入候选。
8. WHEN 已配置 AI 模型且本地候选数量小于候选上限, 变位查询模块 SHALL 请求 AI 模型补充候选。
9. IF AI 模型未配置且本地候选为空, 变位查询模块 SHALL 提示未找到候选并说明配置 AI 模型可增强结果。
10. WHEN 中文查询为中文短语查询且 AI 模型未配置, 变位查询模块 SHALL 从短语中匹配本地词典已有的中文动词含义并生成候选。

### Requirement 3 候选有效性

**User Story:** AS 用户, I want 点选任意候选后都能看到变位, so that 我不必担心候选是无效词。

#### Acceptance Criteria

1. 变位查询模块 SHALL 仅将变位引擎可识别的动词纳入候选列表。
2. WHEN 一个初始候选无法被变位引擎识别, 变位查询模块 SHALL 将其从候选列表移除。
3. IF 全部初始候选均无法被变位引擎识别, 变位查询模块 SHALL 按未找到候选处理。

### Requirement 4 选择候选并展示完整变位

**User Story:** AS 用户, I want 点选候选后看到该动词的全部变位内容, so that 我能完整学习它的变位。

#### Acceptance Criteria

1. WHEN 用户点击某候选动词, 变位查询模块 SHALL 展示该动词的完整变位结果, 其内容与法语查询路径一致。
2. WHEN 用户点击某候选动词, 变位查询模块 SHALL 将搜索框文本更新为所选动词的不定式。
3. WHILE 展示某候选动词的变位结果, 变位查询模块 SHALL 提供返回候选列表的操作。

### Requirement 5 交互与性能

**User Story:** AS 用户, I want 输入中文时界面保持顺畅, so that 我不会因卡顿或旧结果覆盖而困扰。

#### Acceptance Criteria

1. 变位查询模块 SHALL 以异步且可取消的方式执行候选查询。
2. 变位查询模块 SHALL 对连续输入施加不小于 250 毫秒的防抖。
3. WHILE 候选查询进行中, 变位查询模块 SHALL 展示加载状态。
4. WHEN 在旧查询返回前输入发生变化, 变位查询模块 SHALL 丢弃旧查询结果。

### Requirement 6 错误处理

**User Story:** AS 用户, I want 查询失败时得到明确提示, so that 我知道下一步怎么做。

#### Acceptance Criteria

1. IF AI 模型请求失败或超时, 变位查询模块 SHALL 展示失败提示并保留本地候选。
2. IF 本地词典与 AI 模型均无候选, 变位查询模块 SHALL 提供重试操作。
3. WHILE 网络不可用且本地候选非空, 变位查询模块 SHALL 使用本地候选完成查询。

### Requirement 7 现有行为回归保持

**User Story:** AS 现有用户, I want 法语查询体验保持不变, so that 既有使用习惯不受影响。

#### Acceptance Criteria

1. WHILE 输入为法语, 变位查询模块 SHALL 保持现有原形与变体（含省音/缩合形式）查询行为。
2. WHILE 输入为法语, 变位查询模块 SHALL 保持现有变位结果的标签页内容与顺序。
