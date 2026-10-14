# 扫码NFC工具 (qrnfc-tool)

一个纯原生实现的安卓工具 App，集成了 **扫码**、**NFC 读卡**、**LDAP 查询** 三合一。无任何第三方 LDAP 依赖，手写轻量 LDAP 协议客户端，运行稳定、体积小。

- 包名：`com.qrnfc.tool`
- minSdk 24（Android 7.0+），targetSdk / compileSdk 34
- 开发：Java 17 + AGP 8.5.2

## 功能

### 扫码（二维码 / 条形码）
- 相机扫码，**强制竖屏**
- 识别结果直接展示，可**一键复制**
- 内容为链接时，可**打开链接**（交给对应的 App）
- 手电筒辅助照明
- **相册识别**：支持一张图多个码，逐个点选操作
- 二维码 / 条码识别类型全支持（QR / EAN / UPC / Code128 等）
- 商品条码（EAN/UPC 等纯数字条码）附带「**查询商品信息**」按钮，联网查询商品名、品牌、规格等（多数据源：OpenFoodFacts / barcodeapi.org）
- **扫码历史记录**（本地保存 50 条）

### NFC 读卡
- 读取卡片 **UID**
- 同时显示 **十六进制**（HEX，冒号分隔）与 **十进制**（DEC，无符号）
- 显示卡片技术类型
- 读卡震动提示、连续读卡
- **NFC 读卡历史记录**

### LDAP 查询
- 查询**计算机账号是否存在**、查询**用户信息**
- 支持 **任意位置模糊查询**（输入中间一段也能匹配）
- 结果以**列表**展示，点选查看完整详情（操作系统、最后登录时间、部门、邮箱等）
- 支持 LDAPS / 自签名证书、绑定凭据、测试连接
- 连接设置独立页面（查询页右上角设置图标进入）

## 构建

```bash
# 需要: JDK 17、Android SDK (compileSdk 34)
# 首次构建前创建本地签名配置 keystore.properties（已被 .gitignore 排除）:
#   storeFile=release.keystore
#   storePassword=xxxx
#   keyAlias=scan_nfc
#   keyPassword=xxxx

./gradlew assembleRelease
```

## 下载

APK 已通过 GitHub Releases 发布，可在 [Releases 页面](https://github.com/wuxuanzhang/qrnfc-tool/releases) 直接下载安装（所有版本签名一致，可覆盖升级）。

## 版本历史

| 版本 | 说明 |
|------|------|
| v1.0 | 首版：扫码（返回原文/可复制/打开链接）+ NFC 读卡（HEX/DEC） |
| v1.1 | 扫码强制竖屏、相册识别、扫码历史 |
| v1.2 | 扫码手电筒、NFC 连续读卡、NFC 历史 |
| v2.0 | 修复版本号不变 / 图标显示问题，更换图标 |
| v2.1 | 相册一图多码识别与操作 |
| v2.2 | 新增 LDAP 查询（设置+查询一体页） |
| v2.3 | 手写 LDAP 客户端，彻底修复第三方库导致的闪退 |
| v2.4 | LDAP 设置拆独立页、前缀模糊查询、结果列表点选详情 |
| v2.5 | 设置页精简、切换条件清空输入、模糊查询升级为任意位置包含匹配 |
| v2.6 | 商品条码「查询商品信息」按钮（多数据源联网查询） |

## 技术要点

- 手写 LDAP 客户端 `LdapClient.java`：纯 `Socket` + BER 编解码，支持 simple bind、subtree 搜索、substring 模糊过滤、LDAPS（信任自签名证书）。附带 JVM 集成测试 `ldaptest/LdapClientTest.java`。
- 扫码基于 ZXing（`zxing-android-embedded`），多码识别用 `GenericMultipleBarcodeReader`。

## 许可证

本项目仅供学习交流使用。请勿用于非法用途。
