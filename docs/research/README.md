# 调研原始记录

这个目录是写代码之前查资料的存档，保留下来是为了让 README 里的技术判断有出处可查，
而不是"我记得好像是这样"。

> **注意界面方案换过。** 1.4.0 及以前界面做的是 **iOS 26 Liquid Glass** 的模拟，
> 因为渲染问题太多（每帧抓背景、AGSL 着色器首帧要编译、失败还会静默降级），
> 1.5.0 起**整个 UI 层重写为 Material 3 Expressive**。
> 所以这里的 iOS 26 资料是**已废弃方案的记录**，不是当前的实现依据；
> 它的价值在于解释"为什么不用 Liquid Glass 了"。

## 当前实现依据：Material 3 Expressive

| 文件 | 内容 |
|---|---|
| [`raw/…ExpressiveMotionTokens.kt.md`](raw/https___raw_githubusercontent_com_androidx_androidx_androidx_main_compose_material3_tokens_ExpressiveMotionTokens.kt.md) | **看这份**：androidx `material3` 里的弹簧 token 原文（damping / stiffness） |
| [sample-google-dark-palette.py](sample-google-dark-palette.py) | **配色看这份**：从 Google 自家应用深色截图里逐像素采样出 CastColor.dark() 用的那一组值 |

官方规范（2026-10-09 核对，均返回 200）：

| 主题 | 地址 | 本项目的用法 |
|---|---|---|
| 颜色角色 | https://m3.material.io/styles/color/roles | `CastColor` 的角色命名与取值 |
| 状态层 | https://m3.material.io/foundations/interaction/states/state-layers | hover 8% / press 10% / drag 16%，不换填充色 |
| 形状 | https://m3.material.io/styles/shape/shape-morph | `CastShape` 的圆角阶梯与按下形变 |
| 动效 | https://m3.material.io/styles/motion/overview/how-it-works | 空间/效果两族弹簧的区分 |
| 分段按钮 | https://m3.material.io/components/segmented-buttons/specs | `CastSegmentedButton` |
| 输入框 | https://m3.material.io/components/text-fields/specs | `CastTextField` 的缺口浮动标签 |

> m3.material.io 是前端渲染的，抓下来只有标题，所以这里只记地址；
> 能抓成文本的原始 token 已经存进 `raw/`。

## 已废弃方案：iOS 26 Liquid Glass

| 文件 | 内容 |
|---|---|
| [amap-deeplink-and-ios26-report.md](amap-deeplink-and-ios26-report.md) | 高德深链调研（**仍然有效**）+ iOS 26 Liquid Glass 调研（**已废弃**） |
| `raw/` | 抓下来的原始页面（高德 URI 文档、Apple HIG、WWDC25 219 场等），未整理 |

`raw/` 里的文件名是抓取脚本生成的，带 `https___` 前缀，不好看但保留了来源 URL，
出问题时能顺着找回去。约 4 MB，主要是高德旧版 URI 文档的 PDF 和文本转储。

### 仍然有效的结论（高德 / 深链）

- **深链用手册里给的那一个 scheme，不混用**：高德查看用 `androidamap://viewMap`，
  路线用 `amapuri://route/plan/`；两者文档上是分开的页面，没有互相替代的说明。
- **`dev=0` 表示坐标已是 GCJ-02**，高德不用再转。应用自己完成换算，不把这个责任甩给地图。
- **百度走 `coord_type=gcj02`**，让它自己转 BD-09，比在本地转更可靠；`src` 是应用标识，不是密钥。
- **腾讯 `qqmap://` 的参考示例不带 `referer`**，本应用不内置任何密钥，因此按示例的写法来。

### 已废弃的结论（Liquid Glass）

- ~~**iOS 26 Liquid Glass 的公开要点**：纯黑画布、材质本身承担层次（薄白纱 + 顶镜面高光 +
  1dp 亮边）、悬浮胶囊工具栏、同心圆角、scroll edge effect、44pt 最小点击区、
  Reduce Motion 对应 `ANIMATOR_DURATION_SCALE`。~~

  这套做法在真机上会出问题：首帧要编译 GPU 管线（模拟器上约 1.9 秒），
  背景抓取吃主线程，AGSL 需要 API 33+ 且编译失败后静默降级成模糊，高光方向还取决于
  重力传感器的朝向。1.5.0 起整套删掉，换成 Material 3——标准 Canvas 绘制，
  没有背景抓取、没有运行时着色器、没有降级档。

  其中**唯一沿用下来的一条**是 Reduce Motion：仍读 `ANIMATOR_DURATION_SCALE`，
  它现在是 `CastMotion.animationsEnabled()`。
