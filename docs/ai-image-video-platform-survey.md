# AI 生图与视频生成平台调研

调研时间：2026-05-24

> 说明：价格和套餐变化很快，下面以官方页面当前展示为准。

## 结论

如果你要做一个基于 `GPT` 的生图 + 视频统一平台，最值得对标的不是“单一生成器”，而是“统一创作工作台”：

- 图像侧，强竞争点是提示词遵循、文字渲染、风格一致性、编辑能力、商业可用性。
- 视频侧，强竞争点是文本/图生视频、角色一致性、音频、镜头控制、剪辑/延展。
- 产品层面，真正的差异化通常来自工作流，而不是单次生成效果。

## 统一平台形态

建议把产品做成同一套账号下的三个层：

1. 创作层：同一个输入框支持 `出图`、`出视频`、`图生视频`、`局部编辑`。
2. 资产层：同一套素材库管理提示词、参考图、角色卡、风格模板、成片历史。
3. 交付层：同一套下载、分享、API、计费、权限、审核体系。

这样用户不会感知“这是两个产品”，而是“一个平台里能先出图，再直接延展成视频”。

## 模型接入层

平台最好再加一层“模型适配器”：

- 支持自定义 `base URL`。
- 支持自定义 `API Key`。
- 支持按项目切换不同模型提供方。
- 支持 OpenAI 兼容接口，方便接入第三方和私有模型。

这样你的平台就不只是“内置模型调用器”，而是一个真正的多模型创作中台。

## 主流生图平台

| 平台 | 定位 | 核心优势 | 价格/计费 |
|---|---|---|---|
| OpenAI `GPT Image 1.5` | API-first | 提示词遵循强，支持生成和编辑，适合做产品化集成 | API 按 token 计费 |
| Midjourney | 创意生成 | 审美强、社区强、适合风格探索 | Basic $10 / Standard $30 / Pro $60 / Mega $120 每月 |
| Adobe Firefly | 商业创作 | 和 Adobe 生态结合紧，适合设计/营销 | Free / Standard $9.99 / Pro $19.99 / Premium $199.99 每月 |
| Ideogram | 文字图像 | 文字渲染强，适合海报、Logo、包装 | Free / Plus $15 / Pro $42 / Team $20/人/月（年付），Basic 已是 legacy |
| Stability AI | 开发者/自部署 | 开放度高，适合 API、私有化、控制类场景 | API 按 credits 计费 |
| Google Imagen 3 | 云平台 | 企业级、Vertex AI 集成、带水印/安全能力 | Vertex AI 计费，官方按模型与请求类型收费 |

## 主流视频平台

| 平台 | 定位 | 核心优势 | 价格/计费 |
|---|---|---|---|
| OpenAI `Sora 2` | API-first | 同步音频、支持文本/图生成视频，适合平台集成 | 720p $0.10/s；Sora 2 Pro 720p $0.30/s，1024p $0.50/s，1080p $0.70/s |
| Runway | 专业创作 | 生成 + 编辑一体，工作流成熟 | 起价 $12/月，按 editor 计费 |
| Pika | 短视频创作 | 玩法多、上手快、适合社媒内容 | Free / Basic $8 / Standard $28 / Pro $76 / Fancy |
| Luma Dream Machine | 电影感视频 | 画面质感强，支持多档计划 | Plus $30 / Pro $90 / Ultra $300 |
| Kling AI | 亚洲市场强势 | 原生音频、镜头/分镜控制、角色一致性 | 按秒计费，原生音频与多镜头能力强 |
| Google Veo 3 | 云平台 | 画面质量高，音画一体，Vertex AI 集成 | Vertex AI 计费，官方按模型与请求类型收费 |

## 对你的产品建议

1. 先做一个统一入口，用户输入一次，系统自己分发到生图或视频。
2. 把“参考图、风格模板、角色一致性、历史版本、收藏资产”做成核心能力。
3. 视频要和图片共享同一素材库，支持“图直接转视频”和“视频再编辑”。
4. 如果要商业化，优先补“版权/商用说明、团队协作、审核、API、计费”。
5. 把 `模型 URL + API Key` 做成可配置项，作为高级能力和企业版卖点。

## 参考链接

- OpenAI Image API: https://platform.openai.com/docs/guides/image-generation
- OpenAI Pricing: https://platform.openai.com/docs/pricing/
- OpenAI Sora API: https://platform.openai.com/docs/guides/video-generation/
- Midjourney Plans: https://docs.midjourney.com/hc/en-us/articles/27870484040333-Comparing-Midjourney-Plans
- Adobe Firefly Plans: https://www.adobe.com/products/firefly/plans.html
- Ideogram Plans: https://ideogram.ai/pricing?tab=personal
- Ideogram Text Rendering: https://ideogram.ai/features/text-rendering/
- Stability AI Developer Platform: https://platform.stability.ai/pricing
- Stability AI Stable Video: https://stability.ai/stable-video
- Google Imagen 3: https://deepmind.google/models/imagen/
- Google Veo 3: https://deepmind.google/models/veo/
- Runway Pricing: https://runwayml.com/pricing
- Pika Pricing: https://pika.art/pricing?interval=month
- Luma Pricing: https://lumalabs.ai/pricing
- Kling AI Guide: https://app.klingai.com/cn/quickstart/klingai-video-3-model-user-guide
- 通义万相官方入口: https://tongyi.aliyun.com/landing?family=wan
