# BUAA-ClassHopper

和每节课前的打卡或扫码签到彻底告别，轻轻一点即可完成智慧教室签到，让你专注于真正重要的学习与研究，不再为水课所困。

## 项目简介

BUAA-ClassHopper 是一款专为北航（BUAA）学子设计的 Android 应用程序，旨在简化智慧教室的签到流程。通过该应用，用户可以查看每日课程表，并直接进行远程签到，无需依赖传统的物理打卡或扫码方式。

## 主要功能

*   **课程表查询**：支持按日期查询每日课程安排。
*   **一键签到**：在课程卡片上直接点击签到按钮，快速完成签到。
*   **学号管理**：自动保存学号，下次使用无需重复输入。
*   **用户信息展示**：获取并展示用户的基本信息。
*   **公告通知**：接收并查看重要通知公告。
*   **实时连接**：通过 WebSocket 保持实时连接，支持状态指示。

## 技术栈

项目采用 Android 开发技术栈构建：

*   **语言**：[Kotlin](https://kotlinlang.org/)
*   **架构**：MVVM (Model-View-ViewModel)
*   **网络请求**：[OkHttp](https://square.github.io/okhttp/) + [Gson](https://github.com/google/gson)
*   **图片加载**：[Glide](https://github.com/bumptech/glide)
*   **UI 组件**：Material Design Components, ConstraintLayout
*   **构建工具**：Gradle (Kotlin DSL)

## 开发环境配置

### 前置要求
*   Android Studio
*   JDK 17+

### 构建步骤

1.  **克隆项目**
    ```bash
    git clone <repository-url>
    cd BUAA-ClassHopper
    ```

2.  **配置密钥**
    在项目根目录下创建 `local.properties` 文件（如果不存在），并添加以下配置：
    ```properties
    APP_SECRET=your_app_secret_here
    ```
    或者在系统环境变量中设置 `APP_SECRET`。

3.  **构建与运行**
    使用 Android Studio 打开项目，等待 Gradle 同步完成。
    *   Minimum SDK: API 28 (Android 9.0)
    *   Target SDK: API 34 (Android 14)

## 使用说明

1.  打开应用，输入你的学号。
2.  默认显示当天的课程，也可以点击日历图标选择特定日期。
3.  在课程列表中找到需要签到的课程。
4.  点击对应课程卡片上的“签到”按钮即可完成签到。
5.  点击左上角菜单按钮打开侧边栏，可以查看公告、进行设置或验证。

## 许可证

本项目采用 [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE) 许可证。

---

**免责声明**：本项目仅供学习交流使用，请勿用于违反学校规定的用途。
