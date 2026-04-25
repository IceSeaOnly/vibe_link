# 共享协议与 MVP 边界

## 1. 实施目标

本阶段目标是完成一个可运行的端到端 MVP：

- Mac 服务端能采集屏幕并以低复杂度视频流形式提供给 Android。
- Android 客户端能连接 Mac、展示画面、缩放查看并发送控制事件。
- Android 能发送语音识别后的文本到 Mac 当前焦点。
- Android 能触发 Mac 端预设命令并查看状态。
- 双端协议清晰，便于后续将 MJPEG 替换为 WebRTC。

## 2. MVP 技术路线

### 2.1 Mac 服务端

- 目录：`server-mac/`
- 语言：Swift
- 构建：SwiftPM
- 形态：命令行服务进程
- 屏幕流：MJPEG over HTTP
- 控制通道：HTTP JSON API
- 输入模拟：macOS CGEvent + 剪贴板
- 截屏实现：优先使用系统 `screencapture` 命令，后续替换为 ScreenCaptureKit

### 2.2 Android 客户端

- 目录：`client-android/`
- 语言：Kotlin
- 构建：Gradle Wrapper + Android Gradle Plugin
- UI：原生 Android View 或 Jetpack Compose 均可，优先选择实现风险低的方案
- 视频展示：解析 MJPEG multipart stream，渲染为 Bitmap
- 控制通道：HTTP JSON API
- 语音输入：Android `SpeechRecognizer`
- 设备验证：使用已连接 ADB 设备 `4e64cdf5`

## 3. 连接假设

- MVP 默认运行在同一局域网。
- Mac 服务端监听 `0.0.0.0:8765`。
- 服务端启动时打印本机局域网地址和访问 token。
- Android 客户端允许用户输入服务端地址和 token。
- 二维码配对不进入本次实现的硬门槛，但文档保留后续任务。

## 4. 安全边界

MVP 采用共享 token 鉴权：

- 所有 API 必须携带 token。
- `GET /stream?token=<token>` 使用查询参数。
- JSON API 使用 `Authorization: Bearer <token>`。
- token 启动时生成，也允许通过启动参数指定。
- 命令只能执行服务端预设命令，不支持手机端任意 shell。

## 5. HTTP API

### 5.1 健康检查

`GET /health`

响应：

```json
{
  "ok": true,
  "name": "VibeLink Mac Server",
  "version": "0.1.0",
  "streamUrl": "/stream",
  "screen": {
    "width": 1512,
    "height": 982,
    "scale": 2
  }
}
```

### 5.2 屏幕流

`GET /stream?token=<token>`

响应类型：

```text
multipart/x-mixed-replace; boundary=frame
```

每帧为 JPEG：

```text
--frame
Content-Type: image/jpeg
Content-Length: <bytes>

<jpeg bytes>
```

### 5.3 控制事件

`POST /api/control`

请求：

```json
{
  "type": "tap",
  "x": 100,
  "y": 200,
  "screenWidth": 1512,
  "screenHeight": 982
}
```

支持事件：

| type | 字段 | 行为 |
| --- | --- | --- |
| `tap` | `x`, `y` | 单击 |
| `doubleTap` | `x`, `y` | 双击 |
| `rightClick` | `x`, `y` | 右键 |
| `drag` | `fromX`, `fromY`, `toX`, `toY`, `durationMs` | 拖动 |
| `scroll` | `deltaX`, `deltaY` | 滚动 |
| `text` | `text`, `submit` | 粘贴文本，可选回车 |
| `clipboard` | `text` | 写入剪贴板 |

响应：

```json
{
  "ok": true
}
```

### 5.4 快捷文本

`GET /api/quick-texts`

响应：

```json
[
  {
    "id": "continue_fix",
    "name": "继续修复",
    "group": "AI Prompt",
    "content": "继续修复上一个问题，先定位根因，再修改代码并运行相关测试。"
  }
]
```

### 5.5 快捷命令列表

`GET /api/commands`

响应：

```json
[
  {
    "id": "pwd",
    "name": "显示当前目录",
    "command": "pwd",
    "workingDirectory": "/Users/icesea/OtherProjects/vibe_link",
    "requiresConfirmation": false
  }
]
```

### 5.6 执行快捷命令

`POST /api/commands/run`

请求：

```json
{
  "id": "pwd"
}
```

响应：

```json
{
  "ok": true,
  "runId": "run_001",
  "status": "running"
}
```

### 5.7 查询命令运行

`GET /api/commands/runs/<runId>`

响应：

```json
{
  "id": "run_001",
  "commandId": "pwd",
  "status": "succeeded",
  "exitCode": 0,
  "output": "/Users/icesea/OtherProjects/vibe_link\n",
  "startedAt": "2026-04-25T08:00:00+08:00",
  "finishedAt": "2026-04-25T08:00:01+08:00"
}
```

### 5.8 快捷按钮

`GET /api/shortcut-buttons`

响应：

```json
[
  {
    "id": "center_click",
    "name": "点击屏幕中央",
    "x": 756,
    "y": 491,
    "actionType": "tap"
  }
]
```

`POST /api/shortcut-buttons/run`

请求：

```json
{
  "id": "center_click"
}
```

响应：

```json
{
  "ok": true
}
```

## 6. 坐标约定

- 服务端以 macOS 逻辑坐标作为控制坐标。
- Android 端渲染屏幕时记录图片原始宽高。
- Android 点击画面后，应按图片原始尺寸反算 `x`、`y`，再发给服务端。
- 如果 Android 对画面进行了缩放和平移，必须在客户端转换回原图坐标。

## 7. 验收命令

### 7.1 服务端

```bash
cd server-mac
swift build
swift run VibeLinkServer --port 8765
```

### 7.2 客户端

```bash
cd client-android
./gradlew assembleDebug
./gradlew installDebug
```

### 7.3 ADB 验证

```bash
adb devices -l
adb -s 4e64cdf5 shell monkey -p com.vibelink.client 1
adb -s 4e64cdf5 exec-out screencap -p > /tmp/vibelink-client.png
```

