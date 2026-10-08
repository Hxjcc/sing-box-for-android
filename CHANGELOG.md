# 更新记录

## 2026-10-08 — 1.15.0-alpha.10 个人版

### 版本与来源

| 项目 | 上一版 | 本次版本 |
| --- | --- | --- |
| Android 应用 | 1.15.0-alpha.8 / 740 | 1.15.0-alpha.10 / 742 |
| Android 上游源码 | [`3295b6b`](https://github.com/SagerNet/sing-box-for-android/commit/3295b6b35811ba71df6363d6c87bc180acc2e3b9) | [`5c7b4ce`](https://github.com/SagerNet/sing-box-for-android/commit/5c7b4ce969b926063737d059edf7b256c8f56ed0) |
| sing-box 内核源码 | [`b609f95`](https://github.com/SagerNet/sing-box/commit/b609f959f57ce34416c51c7b87ce4a76f2e1df56) | [`fe92ab3`](https://github.com/SagerNet/sing-box/commit/fe92ab3e78a9bb7d448c155ef6906218e2ca5453) |
| 内核显示版本 | 1.15.0-alpha.8 | 1.15.0-alpha.10-fe92ab3 |

本次源码固定于 2026-10-07 检查到的上游分支提交，构建于 2026-10-08 完成。内核取自 `testing`，与官方 `v1.15.0-alpha.10` 发布标签不是同一提交，因此保留提交号后缀以便追溯。

### 上游 Android 更新

- 减少检查 GitHub 更新时的 API 请求次数。[`ef88b1f`](https://github.com/SagerNet/sing-box-for-android/commit/ef88b1f)
- 仅在服务启动完成后显示连接入口，调整服务状态显示。[`568b80e`](https://github.com/SagerNet/sing-box-for-android/commit/568b80e)
- 为全部、活动、已关闭和搜索结果为空的连接列表增加提示。[`5cb7414`](https://github.com/SagerNet/sing-box-for-android/commit/5cb7414)
- 移除远程控制连接失败后的自动重试，简化错误处理。[`49fc144`](https://github.com/SagerNet/sing-box-for-android/commit/49fc144)
- 版本号更新为 `1.15.0-alpha.10`，版本代码更新为 `742`。[`5c7b4ce`](https://github.com/SagerNet/sing-box-for-android/commit/5c7b4ce969b926063737d059edf7b256c8f56ed0)

### 上游内核更新

官方版本摘要中，alpha.9 包含 NaiveProxy 升级及修复改进，alpha.10 为修复改进。以下补充本次所选 `testing` 源码中的主要改动；部分功能仅在使用相应协议或平台时生效。

- NaiveProxy 更新到 `154.0.8037.49-2`，同步相关 Cronet 依赖。[`029fe0b60`](https://github.com/SagerNet/sing-box/commit/029fe0b60)
- 重做转发 NAT，加入 UDP 映射与分片支持。[`bb2b9d92d`](https://github.com/SagerNet/sing-box/commit/bb2b9d92d)
- 修复协议输入校验，涉及规则集二进制、Tailcat 和 HTTP/2 等处理路径。[`6e45e521f`](https://github.com/SagerNet/sing-box/commit/6e45e521f)
- 修复端点按目标地址进行流量背压处理的问题，涉及 WireGuard、OpenVPN、OpenConnect、MASQUE 等组件。[`1690358af`](https://github.com/SagerNet/sing-box/commit/1690358af)
- 修复端点按需恢复运行的问题。[`c31fd587c`](https://github.com/SagerNet/sing-box/commit/c31fd587c)
- 将组件生命周期清理重构为 `adapter.Scope`，同步调整缓存、DNS、出入站和端点的启动与关闭流程。[`813ddfa98`](https://github.com/SagerNet/sing-box/commit/813ddfa98)
- 更新 sing-tun、QUIC、AnyTLS、VMess、Shadowsocks、Tailscale 等相关依赖。具体版本见[本次内核 go.mod](https://github.com/SagerNet/sing-box/blob/fe92ab3e78a9bb7d448c155ef6906218e2ca5453/go.mod)。

官方完整版本说明见[固定到本次源码的上游 Changelog](https://github.com/SagerNet/sing-box/blob/fe92ab3e78a9bb7d448c155ef6906218e2ca5453/docs/changelog.md)。上游分支存在历史重写，本记录依据已固定源码之间的差异整理，没有将仅提交号变化的已有功能全部列为新增。

### 本次适配与保留的定制

- 合并新版连接订阅状态判断，切换远程服务器时重新订阅；断开时清理旧连接并使待处理快照失效。
- 保留连接列表批量刷新、弹窗展开后再加载内容，以及应用图标加载和缓存优化。
- 保留日志页按可见性订阅、显示和暂停缓存各最多 3000 条的限制；暂停日志的语义未改变。
- 保留重载前释放旧内核实例的内存修复、启动 RTT 检测和按配置隔离节点选择记录的定制。
- 保留已移除 Root / LSPosed / Xposed 集成的个人版范围，普通 VPN、分应用代理和 Shizuku 继续保留。
- 对比新旧内核生成的 Android 平台接口、命令回调、Libbox 方法及启动覆盖选项，未发现需要额外 Kotlin API 兼容层的变更。
- 构建脚本改为明确检出固定内核提交，并依据发布标签与提交是否一致生成版本后缀。

构建方式和定制细节见 [CUSTOM_BUILD.md](config/CUSTOM_BUILD.md)。

### 构建与验证

- ARM64，Android 7.0 及以上；包名 `io.nekohasekai.sfa.traffic`，签名与上一版一致，可覆盖安装。
- 内核版本、配置选择缓存隔离、重载旧实例释放等 Go 测试通过；相关包编译通过。
- 4 项日志缓存 JVM 测试通过，Android 编译、发布版关键 Lint 检查、R8 和资源裁剪通过。
- APK 签名、包名、版本号、架构及已移除的特权组件检查通过。
- 独立复现符号裁剪后，确认 APK 中的原生库与新构建内核一致，内嵌版本为 `1.15.0-alpha.10-fe92ab3`。
- 未连接测试手机，实际联网、内存占用和界面帧率尚未真机验证。
