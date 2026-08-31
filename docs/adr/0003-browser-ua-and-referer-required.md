# 抓取视频必须伪装浏览器 UA 与 Referer

视频 CDN（`qxtechdata.qiuxinkaifa.com`）对非浏览器客户端限流/挂起——curl 默认 UA 的 GET 会连接超时，携带浏览器 `User-Agent` + `Referer: https://servicewechat.com/` 即秒回 200/206。后端 HTTP 客户端必须固定携带这两个头，否则下载必超时。后续若有人「简化」掉这段看似多余的请求头，下载会静默失败。
