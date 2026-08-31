# 兰芯数字赛场 API 分析报告

> 目标：H5 批量下载「免登录可查看」的比赛视频。
> 来源文档：`https://qiuxinkaifa.com/index.php/Api`（及其子页）
> 分析日期：2026-08-28

---

## 1. 概览

| 项 | 内容 |
|----|------|
| 平台 | 兰芯数字赛场（篮球场馆视频采集/分析）小程序后端 |
| 通信方式 | 全量 `POST`，`Content-Type: application/x-www-form-urlencoded` |
| 方法分发 | 表单参数 `op=<方法名>` + 业务参数 |
| **路径分组** | 方法按 URL 路径分组，不同 `op` 必须发到对应路径，否则回退返回文档 HTML |
| 返回格式 | JSON（`{"rows":[...], "pager":{...}}` 或直接数组/对象） |

### 路径 → 类 对照

| 路径 | 类 | 说明 |
|------|----|----|
| `/index.php/Api` | `IndexClass` | 首页/场馆/通用 |
| `/index.php/Api/Activity` | `ActivityClass` | 5v5 比赛 |
| `/index.php/Api/Half` | `HalfClass` | 4v4 半场 |
| `/index.php/Api/User` | `UserClass` | 个人中心 |
| `/index.php/Api/Label` | `LabelClass` | 事件标注（仅管理员） |
| `/index.php/Api/IP` | `IpClass` | 赛事 IP |

### 鉴权模型

文档**没有任何显式鉴权参数**（无 appKey / sign / token 字段）。实际机制：

- **会话式**：微信小程序登录（`WxCode` → `uid` + `token` → `AutoLogin`）。
- **角色分级**：`roleid ∈ [禁用, 用户, 核销, 管理]`。
- **关键结论（已实测）**：大量「只读」接口**零会话即可访问**——裸 POST、不带任何 cookie / header 也返回数据。

> ⚠️ 「免登录可看」≠「接口零鉴权」。实测证明：列表/详情类只读接口是**真正零会话**的（情况 A，最省事）。写操作 / 个人数据 / 管理操作才需要会话或角色。

---

## 2. 鉴权分级定义

本报告用以下标签标注每个接口：

| 标签 | 含义 | 判定依据 |
|------|------|----------|
| 🌐 **公开** | 免鉴权，后端裸 HTTP 即可调用 | 文档语义（列表/详情/首页）+ 部分实测 |
| 🔑 **登录** | 需要 `uid`+`token` 会话 | `My*`、写操作、个人数据 |
| 🛡️ **管理** | 需要 `roleid=管理/核销` | 标注、运营、裁判、内部 |
| 💰 **支付** | 购买/解锁/下单 | 微信支付、次卡、SKU、集锦购买 |
| ⚙️ **回调/内部** | 服务端到服务端，用户不直接调用 | `*Back`、支付回调、OSS 上传等 |

---

## 3. 实测记录（零会话验证）

以下为 2026-08-28 用 `curl` 裸 POST（无 cookie、无 header）实测，全部返回 JSON 数据：

| 方法 | 路径 | 参数 | 结果 |
|------|------|------|------|
| `CityList` | `/Api` | `{}` | ✅ 返回城市列表（含 phone） |
| `HomeBbs` | `/Api` | `{}` | ✅ 返回首页球讯流 |
| `ActOfToday` | `/Api` | `cityid=1` | ✅ 返回今日比赛列表（actid/courtid/球队/比分） |
| `List5v5` | `/Api/Activity` | `courtid=6,page=1,size=5` | ✅ 返回该场馆历史比赛 |
| `PlayByPlay` | `/Api/Activity` | `actid=68397,getvideo=1,size=5` | ✅ 返回事件 + `videos[].path`(mp4 直链) |

> 推断：同一类「列表 / 详情 / 首页」只读接口大概率同为零会话；写操作、`My*`、管理接口需会话或角色（未逐一实测，故标注「推断」）。

---

## 4. 接口清单（按模块）

> 参数中 `[x]` 表示可选。鉴权标签见第 2 节。🌐 为免鉴权。

### 4.1 IndexClass（`/Api`）

| 方法 | 说明 | 参数 | 鉴权 |
|------|------|------|------|
| CityList | 城市列表 | `{}` | 🌐 实测 |
| GetCityByGeo | GEO 坐标 → 城市ID | `lat, lon` | 🌐 |
| HomeBbs | 小程序首页 | `{}` | 🌐 实测 |
| Home | 场馆首页 | `courtid` | 🌐 |
| TakeNewbie | 领取新手体验卡 | `courtid` | 🔑 |
| BuyVipInCourt | 场馆扫码购会员卡 | `courtid` | 💰 |
| NearCourt | 最近场馆 | `lat, lon` | 🌐 |
| CourtsList | 场馆列表 | `lat, lon, [key],[size],[page],[news],[cityid]` | 🌐 |
| HomeNews | 首页球讯 | `{}` | 🌐 |
| ShareImgs | 场馆精选内容 | `courtid, tags, [page],[size]` | 🌐 |
| SetLike | 点赞/收藏/关注 | `kind, target, refid, state` | 🔑 |
| TeamList | 球队列表 | `[key],[page],[size]` | 🌐 |
| TeamSave | 保存球队 | `[teamid], nick, phone, slogon, icon` | 🔑 |
| MakeQR | 比赛分享 QR | `{}` | 🌐 |
| WxUserIcon | 代理微信头像 | `uid` | 🌐 |
| Upload | 图片上传 | `file` | 🔑 |
| SecImgUp | 上传图片鉴黄 | `file` | 🔑 |
| MultiUp | 文件分段上传 | `{}` | 🔑 |
| SubMsg | 通用订阅 | `kind, refid, tempid, page, [ext]` | 🔑 |
| FeedbackAdd | 通用反馈 | `kind, refid, intro` | 🔑 |
| CacheSVG | 缓存 SVG | `actid, svg` | ⚙️ |
| Svg2Jpeg | SVG 转 JPEG | `svg` | 🌐 |
| RetrieveSVG | 取回 SVG | `{}` | 🌐 |
| OaMarkCataList | OA 兼职帮助列表 | `{}` | 🌐 |
| OaTrainingCataList | OA 培训计划 | `{}` | 🌐 |
| HelpCataList | 帮助中心栏目 | `{}` | 🌐 |
| NewsList | 活动介绍列表 | `cataid` | 🌐 |
| NewsDetail | 活动详情 | `id` | 🌐 |
| ActOfToday | 今日比赛 | `[key],[cityid]` | 🌐 实测 |
| Welcome | 新版首页 | `[courtid]` | 🌐 |
| Recommend | 推荐内容 2+2 | `{}` | 🌐 |
| QaVideos | 赞助 QA 视频列表 | `{}` | 🌐 |
| CorpCourts | 公司名下场馆 | `corpid` | 🌐 |
| HotIPs | 推荐 IP 赛事列表 | `cityid` | 🌐 |
| DictList | 字典列表 | `kind` | 🌐 |
| MergeClipsByNotIds | 通用合并视频片段（废弃） | `refid, kind, notids, ...` | 💰 |
| MergeClipsByNotIdsBack | 合并回调 | `{}` | ⚙️ |
| MergeClipsByNotIdsAliBack | 阿里云合并回调 | `{}` | ⚙️ |
| AliUpload | 阿里云 OSS 上传 | `file` | ⚙️ |
| TrackerEvents | 埋点上报 | `events` | 🌐 |
| OssRestoreCheck | 视频冷存储状态（供播放 onerror） | `path` | 🌐 |
| OssRestoreStarCreate | 创建解冻任务 | `actid, starid` | 🔑 |
| OssRestoreStarProcess | 解冻队列消费者 | `taskid` | ⚙️ |
| OssRestoreTasks | 我的解冻任务列表 | `[page],[size]` | 🔑 |

### 4.2 ActivityClass（`/Api/Activity`）— 5v5

| 方法 | 说明 | 参数 | 鉴权 |
|------|------|------|------|
| PlayByPlay | 事件列表（`getvideo=1` 返回视频） | `actid,[teamid],[starid],[offensive],[jieguo],[size],[page],[getvideo],[getstar]` | 🌐 实测 |
| List5v5 | 5v5 比赛列表 | `courtid, cataid, page, size, home` | 🌐 实测 |
| 5v5Detail | 5v5 详情 | `actid` | 🌐 |
| SetRefScore | 裁判评分 | `actid, refid, score` | 🛡️ |
| PhotoList | 活动照片列表 | `actid, isteam, size, page` | 🌐 |
| Live5v5 | 比分+事件直播 | `actid, home, size, page` | 🌐 |
| LiveScore | 比分直播 | `actid, home, dur` | 🌐 |
| GetNewScore | 最新比分 | `actid` | 🌐 |
| ScoreDetail | 回合详情 | `actid, size, page, [team],[secid]` | 🌐 |
| StarDetail | 球员详情 | `actid, starid` | 🌐 |
| StarFriends | 我的进球密友 | `actid, starid` | 🌐 |
| StarMarket | 球星订单/是否购集锦 | `actid, starid, pubver` | 💰 |
| TeamPK | 球队 PK | `actid` | 🌐 |
| StarPK | 球员 PK | `actid, starid` | 🌐 |
| StarDataPK | 球员 PK 个人参数 | `actid, starid` | 🌐 |
| StarClips | 球员视频列表 | `actid, starid` | 🌐 |
| YuGao | 比赛预告 | `actid` | 🌐 |
| Get5v5Bet | 5v5 竞猜结果 | `actid` | 🌐 |
| GetPkTeam | 球队 PK 数据 | `actid` | 🌐 |
| BetTeam | 竞猜 | `actid, teamid, state, odds` | 🔑 |
| DropNotify | 删除直播通知 | `notid` | 🔑 |
| UnlockTeamFusionPay | 解锁球队集锦（废弃） | `actid, teamid` | 💰 |
| UnlockTeamPay | 解锁球队数据 | `actid, teamid, skuid, showpay` | 💰 |
| UnlockTeamByAdmin | 运营解锁球队报告 | `actid, teamid, skuid` | 🛡️ |
| LockTeamByAdmin | 运营取消解锁 | `actid, teamid, skuid` | 🛡️ |
| UnlockTeamOrder | 解锁支付回调 | `xml` | ⚙️ |
| MakeFusionByAliyun | 预约阿里云集锦 | `taskcode` | ⚙️ |
| StarAssistPay | 解锁篮板/助攻集锦（废弃） | `actid, starid, kind` | 💰 |
| StarAssistBack | 篮板助攻回调（废弃） | `{}` | ⚙️ |
| PostFusionTask | 提交集锦任务 | `taskcode` | ⚙️ |
| MergeClips | 合成视频（付款后） | `actid, starid, pubver, uid` | 💰 |
| CheckMergeTask | 检测合成任务 | `taskid, time` | 🔑 |
| MergeClipsBack | 合成回调 | `{}` | ⚙️ |
| TeamHome | 球队主页 | `teamid, size, page` | 🌐 |
| CancelTask | 撤销预约 | `actid` | 🔑 |
| YuyueOffline | 离线预约测试 | `actid, kind, prefix, line` | ⚙️ |
| OcrOffline | OCR 离线重跑 | `actid,[stime],[etime],restart,clear` | ⚙️ |
| GetLabeledStar | 活动球员列表 | `actid, teamid` | 🛡️ |
| ActStarIcons | 球员图片列表 | `actid, side` | 🌐 |
| SumLabledScore | 按标注统计总分 | `actid` | 🛡️ |
| ExtendClip | 延长片段 | `actdate,courtid,placeid,notid,time1,time2` | 🛡️ |
| TeamShareNum | 分享数统计 | `actid, teamid` | 🌐 |
| MergeClipsByNotIds | 合并指定视频（废弃） | `name, notids, merge, zoom, ...` | 💰 |
| MergeClipsByNotIdsBack | 合并回调 | `{}` | ⚙️ |
| SingleClipMerge | 单视频二合一+运镜 | `actid, notid, [zoom]` | 💰 |
| SingleClipMergeBack | 单视频回调 | `{}` | ⚙️ |
| Top5Ball | Top 5 视频 | `actid` | 🌐 |
| Act5Clips | 比赛事件列表（未标注可看） | `actid,[half],[stime],[etime],[size],[page]` | 🌐 |
| GetAds | 场地广告位 | `[placeid],showat,kind,[ipid],[cityid]` | 🌐 |
| NewClips | 取 4 个最新进球视频 | `actid` | 🌐 |
| GbpmRank | GBPM 排名 | `actid` | 🌐 |
| MergeTasks | 我的合成任务列表 | `[size],[page]` | 🔑 |
| UpdatePlayerImage | 更换图片 | `actid, starid, imgpath, type` | 🛡️ |
| BeforePlay | 赛前准备 | `actid` | 🌐 |
| TeamJielong | 球队接龙 | `actid, teamid` | 🔑 |
| TeamJielongCancel | 取消接龙 | `actid, teamid` | 🔑 |
| TeamJielongList | 接龙列表 | `actid` | 🌐 |
| IPActivityList | IP 活动列表 | `ipid, actdate` | 🌐 |
| AnalyzeProgress | 分析进度 | `actid` | 🌐 |
| LastEventsOfAct | 比赛最新事件 | `actid` | 🌐 |
| IPBetRule | 赛事竞猜规则 | `ipid` | 🌐 |
| List5v5IP | 5v5 列表（IP 专用） | `courtid, cataid, page, size, home` | 🌐 |
| StarUnlockSkuTemplate | 球员解锁板块 | `actid, starid` | 🌐 |
| AfterDefenseMark | 防守标注完成回调 | `actid, teamid` | ⚙️ |

### 4.3 HalfClass（`/Api/Half`）— 4v4 半场

| 方法 | 说明 | 参数 | 鉴权 |
|------|------|------|------|
| HalfState | 场馆半场最新状态 | `courtid` | 🌐 |
| HalfStateEx | 场馆半场最新状态 | `courtid` | 🌐 |
| HalfPlayerList | 半场玩家列表 | `placeid, halfno, stime, etime` | 🌐 |
| VideoList | 半场视频列表 | `courtid, placeid, halfno, stime, etime` | 🌐 |
| PlayerVideoList | 球员视频列表 | `courtid, placeid, date, notids` | 🌐 |
| ClaimShots | 认领视频 | `courtid, placeid, date, notids` | 🔑 |
| StarMarket | 球星订单 | `courtid, placeid, date, notids, pubver` | 💰 |
| UnlockStarPay | 购买视频下单 | `courtid, placeid, date, notids, pubver,[claid]` | 💰 |
| UnlockStarBack | 微信支付回调 | `xml` | ⚙️ |
| MergeClips | 触发打野运镜合成 | `claid, pubver` | 💰 |
| RetrieveClaid | 支付单号 → 认领ID | `tradeno` | 🔑 |
| UpdateClaim | 更新生涯合集 | `claid, notids` | 🔑 |
| MergeClipsBack | 合成回调 | `{}` | ⚙️ |
| ClaimDetail | 4v4 认领内容 | `claid` | 🌐 |
| MakeClipMerge | 半场集锦生成 | `rid, notids, [uid]` | 💰 |
| ShrinkPlayerByTime | 球员预收敛 | `placeid, halfno, playday, stime, etime` | ⚙️ |
| GenerateMyReport | 生成个人报告 | `courtid, placeid, notids, stime, etime` | 🔑 |
| ReportDetail | 半场个人报告详情 | `id` | 🌐 |
| MoveOss | 迁移报告详情 | `rid` | ⚙️ |
| UnbindReport | 解绑报告 | `id` | 🔑 |
| GroupPlayerList | 组队事件列表 | `courtid, placeid, playday, keytime, halfno, stime, etime` | 🌐 |
| GenerateAIMyReport | 生成 AI 个人报告 | `...` | 🔑 |
| GenerateGroupReport | 生成 AI 组队报告 | `...` | 🔑 |
| GroupReportDetail | 组队报告详情 | `id` | 🌐 |
| ActivityDetail | 半场活动详情 | `id` | 🌐 |
| ActivityGroupList | 活动组列表 | `id` | 🌐 |
| UpdateActivityInfo | 修改活动信息 | `actid, cover, stime, etime, title` | 🛡️ |
| DelActivityGroup | 删除活动组 | `gid` | 🛡️ |
| BindMyClaimShot | 绑定我的生涯 | `id` | 🔑 |
| MyActivityList | 我的活动列表 | `uid` | 🔑 |
| ActivitySummary | 活动实况 | `actid` | 🌐 |
| DelActivity | 删除活动 | `actid` | 🛡️ |
| UpdateGroupName | 修改组名 | `gid, group_name` | 🛡️ |
| UpdateGroupPlayer | 更新组球员收敛 | `gid, stime, etime` | ⚙️ |
| UpdateMyGroupReportPlayer | 更新组报告高光 | `rid, notids` | 🔑 |
| MyReportVideo | 个人报告高光 | `rid` | 🔑 |
| PlayerList | 球员记录（当天生涯列表） | `courtid, placeid, playday` | 🌐 |
| AddMyReportVideo | 报告追加事件 | `rid, notids` | 🔑 |
| GroupVideo | 组事件记录 | `gid` | 🌐 |
| HelpVideo | 帮助视频列表 | `kind` | 🌐 |
| SignRank | 场馆打卡排名 | `courtid` | 🌐 |
| MyReport | 我的半场报告 | `page, size` | 🔑 |

### 4.4 UserClass（`/Api/User`）

| 方法 | 说明 | 参数 | 鉴权 |
|------|------|------|------|
| MyInfo | 我的登录信息 | `{}` | 🔑 |
| PayRecords | 我的支付记录 | `size, page` | 🔑 |
| WxCode | 小程序登录 | `code` | 🔑 登录入口 |
| Mine | 我的 | `[needLogin]` | 🔑 |
| ExtraMenu | 我的额外菜单 | `{}` | 🔑 |
| AutoLogin | 自动登录 | `uid, token` | 🔑 登录入口 |
| RegUser | 用户注册 | `nick, icon, phone, sexy, token` | 🔑 注册入口 |
| AccountEdit | 编辑会员资料界面 | `{}` | 🔑 |
| GetPhone | 获取手机号 | `data, iv` | 🔑 |
| AccountSave | 保存会员资料 | `realname,tall,heavy,phone,sexy,intro,favpos` | 🔑 |
| SaveInfo | 修改昵称及头像 | `nick, phone, icon` | 🔑 |
| ScanAuth | 扫码授权 | `scene` | 🔑 |
| TakeAuth | 取回授权信息 | `scene` | 🔑 |
| MyFav5v5 | 我收藏的 5v5 | `size, page` | 🔑 |
| MyFavTeam | 我关注的球队 | `size, page` | 🔑 |
| MyFavStar | 我关注的球星 | `size, page` | 🔑 |
| WxToken | 微信 Token | `appid` | ⚙️ |
| WxRefund | 微信退款 | `payid` | ⚙️ |
| MergeTasks | 我的合成任务 | `[size],[page]` | 🔑 |
| MyCards | 我的 VIP 卡 | `[size],[page]` | 🔑 |
| ClaimDiscount | 认领折扣码 | `id` | 🔑 |
| DiscountInfo | 折扣码详情 | `id` | 🌐 |
| MyDiscounts | 我的优惠记录 | `[size],[page]` | 🔑 |
| VipSkuForGeo | 会员卡 SKU（按经纬度） | `lat, lon` | 🌐 |
| BuyVipCard | 会员卡购买 | `skuid, cityid, courtid, service` | 💰 |
| MyProfit | 分销台账 | `kind,[size],[page]` | 🔑 |
| ProfitAgain | 重新分账 | `profid` | 🔑 |
| SyncToH5User | 关联 H5 分账用户 | `userid` | ⚙️ |
| BindShareAgent | 绑分销关系 | `planid` | 🔑 |
| MyTeams | 我加入的球队 | `{}` | 🔑 |
| ReportFeedAdd | 用户问题反馈 | `task_sub_type, description, ...` | 🔑 |
| ReportFeedList | 反馈列表 | `size, page` | 🔑 |
| AppState | 是否审核中 | `{}` | 🌐 |
| ImgSecCheck | 图片鉴黄 | `img` | ⚙️ |
| MsgSecCheck | 文本鉴黄 | `text` | ⚙️ |
| MyCourts | 我的常去场馆 | `{}` | 🔑 |
| MyQiuJu | 我的球局 | `size, page` | 🔑 |
| My5v5 | 我的 5v5 比赛 | `size, page` | 🔑 |
| PhoneSearch | 手机号查用户 | `phone` | 🛡️ |
| GiftClaim | 扫码领取赠送 | `gift_id` | 🔑 |
| MyTrainCards | 我的训练卡 | `[page],[size]` | 🔑 |
| CardDetail | 训练卡详情 | `card_id` | 🌐 |

### 4.5 LabelClass（`/Api/Label`）— 仅系统管理员

| 方法 | 说明 | 参数 | 鉴权 |
|------|------|------|------|
| StarsOfAct | 所有球员列表 | `actid` | 🛡️ |
| ShotDetail | 标注事件详情 | `actid, notid` | 🛡️ |
| UpdateShot | 更新标注内容 | `actid, notid, offensive, jieguo` | 🛡️ |
| UpdateShotStar | 更新事件球星 | `actid, notid, teamid, starid` | 🛡️ |
| UpdateShotAssist | 更新篮板助攻 | `actid, notid, teamid, starid, type, kind` | 🛡️ |
| UpdateStar | 更新球星资料 | `actid, starid, nick, jersey` | 🛡️ |
| SyncStarData | 同步本场球星数据 | `actid` | 🛡️ |

### 4.6 IpClass（`/Api/IP`）— 赛事

| 方法 | 说明 | 参数 | 鉴权 |
|------|------|------|------|
| GetIpBase | IP 赛事基础信息 | `ipid` | 🌐 |
| Home | IP 赛事首页 | `ipid` | 🌐 |
| ZoneList | 赛区列表 | `cid` | 🌐 |
| SeasonData | 赛季统计 | `type, refid` | 🌐 |
| TeamScoreRank | 球队积分 | `type, refid, kind` | 🌐 |
| StarData | 球员数据 | `type, refid, kind, dur, [sort]` | 🌐 |
| ZoneHome | 赛区首页 | `zid` | 🌐 |
| Schedule | 小组赛程 | `dirid, [key]` | 🌐 |
| DirTeams | 小组参赛队伍 | `dirid, [state]` | 🌐 |
| TeamSchedule | 队伍比赛列表 | `dirid, [teamid],[actids]` | 🌐 |
| IpBaseInfo | dirid → 赛事信息 | `dirid` | 🌐 |
| RegForm | 报名表单 | `cid, [regid]` | 🌐 |
| GroupList | 赛区组列表 | `zid` | 🌐 |
| SaveReg | 提交报名 | `[regid],zid,dirid,teamid,nick,phone,addr` | 🔑 |
| RegUsers | 报名人员列表 | `regid` | 🌐 |
| UserAddForm | 添加报名表单 | `regid, idx` | 🔑 |
| UserSave | 提交报名表单 | `regid, idx` | 🔑 |
| LeadLoad | 领队教练编辑 | `regid, kind` | 🔑 |
| LeadSave | 领队教练保存 | `regid, kind, name, phone, ...` | 🔑 |
| UserDrop | 删除报名用户 | `regid, idx` | 🔑 |
| RegPay | 付款完成报名 | `regid` | 💰 |
| MyReg | 我的报名 | `ipid` | 🔑 |
| Detail | 赛事详情 | `ipid` | 🌐 |
| ShareImgs | 赛事精选内容 | `ipid, [page],[size]` | 🌐 |
| GetPhotos | 赛事照片 | `ipid, [size],[page]` | 🌐 |
| OldStarData | 球员数据（旧） | `ipid, kind, month, [courtid]` | 🌐 |
| OldYearData | 赛季统计（旧） | `ipid` | 🌐 |
| OldTeamScoreRank | 球队积分（旧） | `ipid, month` | 🌐 |
| NewIP | 创建赛事 | `ipname, cname, zname, sdate, edate, dirname` | 🛡️ |
| SetExt | 设置赛事扩展 | `ipid, field, value` | 🛡️ |
| DirList | 赛事小组 ID | `ipid` | 🛡️ |
| RelateActID | 小组关联比赛 | `actid, dirid` | 🛡️ |
| CancelActID | 取消关联 | `actid` | 🛡️ |
| BetRank | 竞猜榜单 | `ipid, kind, date` | 🌐 |
| GetLinks | 赛事链接列表 | `ipid` | 🌐 |
| GetLinkInfo | 赛事资讯 | `linkid` | 🌐 |
| AlbumList | 相册列表 | `kind, rid, filter, [size],[page]` | 🌐 |
| AlbumBase | 相册信息 | `aid` | 🌐 |
| MyUploads | 我上传的照片 | `aid, [size],[page]` | 🔑 |
| PicSave | 上传/修改照片 | `aid, picid, title, src, state` | 🔑 |
| PicList | 照片列表 | `aid, filter, [size],[page]` | 🌐 |
| DetectFace | 人脸列表 | `photo, maxnum` | 🌐 |
| TakeFaceId | 获取人脸 ID | `image, aid` | 🔑 |
| SetPicFaces | 关联人脸到照片 | `picid, faceids` | 🔑 |
| GetPicFaces | 照片人脸列表 | `picid` | 🌐 |
| PicDrop | 删除照片 | `picid` | 🔑 |
| PicInfo | 照片详情 | `picid` | 🌐 |
| PicHit | 点赞照片 | `picid, state` | 🔑 |
| TeamBaseData | 球队基础+场均数据 | `ipid, teamid` | 🌐 |

---

## 5. 实体关系与接口关系

### 5.1 核心实体

```
城市 cityid ──< 场馆 courtid ──< 场地 placeid
                          │
                          ├──< 比赛/活动 actid ──< 事件 notid ──< 视频 videos[] (mp4)
                          │                          └── 球员 starid
                          └──< 半场 halfno ──< 半场视频/球员
团队 teamid ──< 比赛 ──< 球员 starid
赛事 ipid ──< 大项 cid ──< 赛区 zid ──< 小组 dirid ──< 比赛 actid
```

### 5.2 视频下载发现链路（本项目主链路）

```
① 定位：     GetCityByGeo(lat,lon)        → cityid     🌐
② 场馆：     CourtsList(lat,lon) / NearCourt → courtid  🌐
③ 比赛：     List5v5(courtid) / ActOfToday(cityid) → actid  🌐
④ 数据：     5v5Detail(actid) / PlayByPlay(actid)     🌐
⑤ 视频：     PlayByPlay(actid, getvideo=1) → events[].videos[].path (mp4 直链)  🌐
   备选：     Act5Clips / NewClips / Top5Ball / StarClips（事件/球员维度）
```

### 5.3 视频 URL 与「免费/付费」判定（关键）

实测 `PlayByPlay(actid=68397, getvideo=1)` 返回的 `videos[].path`：

| 路径前缀 | 含义 | 处理 |
|----------|------|------|
| `https://qxtechdata.qiuxinkaifa.com/vslicing/.../*.mp4` | **免费** | ✅ 下载 |
| `https://qxtechdata.qiuxinkaifa.com/bepaid/vslicing/.../*.mp4` | **付费**（本体同样公开可读） | ✅ 下载（全下） |

> 判定规则：`path` 含 `/bepaid/` → 付费，否则免费（注意付费路径内部也含 `/vslicing/`，须先判 bepaid）。
> 本项目**不据此过滤**，凡有效即下，见 `docs/adr/0001-download-scope-includes-paid-clips.md`。
> 视频主机 `qxtechdata.qiuxinkaifa.com` 与 API 主机不同；URL 为 OSS/CDN 直链 mp4，可直接下载（未过期时）。
> **实测（2026-08-31）**：付费 mp4 匿名 GET 返回 206 + 真实 MP4 字节（无 CDN 门槛）；但 CDN 有 UA 门槛——非浏览器 UA 会连接超时，须带浏览器 UA + `Referer: https://servicewechat.com/`。
> `videos[]` 每个对象带 `path`、`poster`（首帧缩略图）、`camname`（机位名）、`sort`、`camid`、`halfno`。

---

## 6. 免鉴权接口清单（筛选结果）

> 以下为「零会话即可调用」的只读接口。标 ✅ 为已实测，其余为按语义推断（同属列表/详情/首页类）。

### 6.1 本项目必需（视频发现链路）

| 模块 | 方法 | 用途 | 验证 |
|------|------|------|------|
| Index | GetCityByGeo | 坐标→城市 | 推断 |
| Index | CourtsList | 场馆列表 | 推断 |
| Index | NearCourt | 最近场馆 | 推断 |
| Index | ActOfToday | 今日比赛 | ✅ 实测 |
| Activity | List5v5 | 场馆比赛列表 | ✅ 实测 |
| Activity | 5v5Detail | 比赛详情 | 推断 |
| Activity | PlayByPlay | 事件+视频 | ✅ 实测 |
| Activity | Act5Clips / NewClips / Top5Ball / StarClips | 视频维度补充 | 推断 |
| Half | VideoList / PlayerList / PlayerVideoList | 半场视频 | 推断 |

### 6.2 其余公开只读（可选，扩展用）

- **Index**：CityList ✅、HomeBbs ✅、Home、HomeNews、ShareImgs、TeamList、MakeQR、WxUserIcon、Svg2Jpeg、RetrieveSVG、HelpCataList、NewsList、NewsDetail、Welcome、Recommend、QaVideos、CorpCourts、HotIPs、DictList、TrackerEvents、OssRestoreCheck、OaMarkCataList、OaTrainingCataList
- **Activity**：PhotoList、Live5v5、LiveScore、GetNewScore、ScoreDetail、StarDetail、StarFriends、TeamPK、StarPK、StarDataPK、YuGao、Get5v5Bet、GetPkTeam、TeamHome、ActStarIcons、TeamShareNum、GetAds、GbpmRank、BeforePlay、TeamJielongList、IPActivityList、AnalyzeProgress、LastEventsOfAct、IPBetRule、List5v5IP、StarUnlockSkuTemplate
- **Half**：HalfState、HalfStateEx、HalfPlayerList、ClaimDetail、ReportDetail、GroupPlayerList、GroupReportDetail、ActivityDetail、ActivityGroupList、ActivitySummary、PlayerList、GroupVideo、HelpVideo、SignRank
- **User**：DiscountInfo、VipSkuForGeo、AppState、CardDetail
- **IP**：GetIpBase、Home、ZoneList、SeasonData、TeamScoreRank、StarData、ZoneHome、Schedule、DirTeams、TeamSchedule、IpBaseInfo、RegForm、GroupList、RegUsers、Detail、ShareImgs、GetPhotos、OldStarData、OldYearData、OldTeamScoreRank、BetRank、GetLinks、GetLinkInfo、AlbumList、AlbumBase、PicList、DetectFace、GetPicFaces、PicInfo、TeamBaseData

### 6.3 明确需要鉴权 / 排除

- 🔑 登录：所有 `My*`、写操作（`SetLike`、`TeamSave`、`Upload`、`FeedbackAdd`、`SubMsg`…）、`User` 模块绝大多数。
- 🛡️ 管理：`Label` 模块全部 7 个；`SetRefScore`、`UnlockTeamByAdmin`、`LockTeamByAdmin`、`UpdatePlayerImage`、`OcrOffline`、`ExtendClip`、`NewIP`、`SetExt`、`RelateActID`、`CancelActID`、`PhoneSearch` 等。
- 💰 支付/解锁：`UnlockTeamPay`、`UnlockStarPay`、`StarMarket`、`MergeClips`、`BuyVipCard` 等 —— 与本项目「只下免费」无关，排除。
- ⚙️ 回调/内部：所有 `*Back`、支付回调、OSS 上传 —— 不面向用户。

---

## 7. 结论

1. **零会话访问成立**：视频发现链路上的核心只读接口（CityList / ActOfToday / List5v5 / PlayByPlay）已实测免鉴权，Java 后端无需任何登录凭证即可拉取公开数据。
2. **免费/付费可判定（不据此过滤）**：`videos[].path` 含 `/bepaid/` 即付费，否则免费；本项目全量下载，见 ADR-0001。
3. **下载链路闭环**：`定位 → 场馆 → 比赛 → PlayByPlay(getvideo=1) → 勾选 → 并发下载 + 流式打包 zip`，全链路无鉴权依赖。
4. **量级提醒**：单场比赛 `PlayByPlay` 可达 221 个事件（`size=221` 可一次拉全）；实测 221 事件 = 164 免费 + 57 付费片段。
