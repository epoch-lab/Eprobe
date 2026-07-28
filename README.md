# FRESH Backend

本项目是基于 Spring Boot 和 Maven 构建的后端服务。启动前必须准备可用的 MySQL 与 Redis，并配置 `config_helper` 脚本列出的全部环境变量。

## 运行环境

- JDK 25
- 可用的 MySQL 服务
- 可用的 Redis 服务
- SMTP 邮件账号（注册验证码等邮件功能需要）
- 无需单独安装 Maven，项目已提供 Maven Wrapper

## 启动前准备

### 1. 准备 MySQL

创建一个空数据库和具备该数据库访问权限的专用账号。例如：

```sql
CREATE DATABASE app_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'app_user'@'%' IDENTIFIED BY 'change-this-password';
GRANT ALL PRIVILEGES ON app_db.* TO 'app_user'@'%';
FLUSH PRIVILEGES;
```

应用启动时会通过 Flyway 自动执行数据库建表和迁移。已有生产数据库在升级前应先备份。

### 2. 准备 Redis

确保 Redis 已启动，并确认主机、端口、密码和数据库编号可用。认证验证码、登录失败锁定和 JWT 黑名单依赖 Redis，因此 Redis
不可用时项目无法正常运行。

### 3. 配置环境变量

项目提供以下辅助脚本：

- Linux/macOS/Git Bash：`config_helper/config-env.sh`
- Windows PowerShell：`config_helper/config-env.ps1`

先编辑与你的系统对应的脚本，将脚本配置区中的每一项替换为实际部署值。下面是脱敏后的参考模板，仅用于展示格式：

```dotenv
SERVER_PORT=8080
DB_URL=jdbc:mysql://127.0.0.1:3306/app_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=app_user
DB_PASSWORD=change-this-database-password
JWT_SECRET=replace-with-a-random-secret-at-least-32-characters
JWT_COOKIE_SECURE=false
JWT_COOKIE_SAME_SITE=Strict
AUTH_CACHE_TYPE=redis
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=change-this-redis-password
SPRING_DATA_REDIS_DATABASE=0
AUTH_EMAIL_CODE_IP_SEND_WINDOW_SECONDS=3600
AUTH_EMAIL_CODE_MAX_IP_SEND_COUNT=20
AUTH_EMAIL_CODE_GLOBAL_SEND_WINDOW_SECONDS=60
AUTH_EMAIL_CODE_MAX_GLOBAL_SEND_COUNT=100
AUTH_TRUST_FORWARD_HEADERS=false
AUTH_TRUSTED_PROXIES=
APPLICATION_ALLOWED_GRADES=YEAR_1
APP_STORAGE_ROOT=./storage
APP_STORAGE_CHUNK_SIZE=0B
APP_STORAGE_CLEANUP_ENABLED=true
APP_STORAGE_TEMP_SESSION_TTL=24h
APP_STORAGE_ORPHAN_FILE_TTL=24h
APP_STORAGE_ALLOWED_EXTENSIONS=pdf,docx,zip,png,jpg
APP_STORAGE_ALLOWED_CONTENT_TYPES=application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/zip,image/png,image/jpeg
TASK_ATTACHMENT_MAX_SIZE=20MB
MULTIPART_MAX_FILE_SIZE=25MB
MULTIPART_MAX_REQUEST_SIZE=30MB
APP_AUDIT_MAJOR_EVENT_LOG_FILE_PATH=./logs/business-audit.log
MAIL_SMTP_HOST=smtp.example.com
MAIL_SMTP_PORT=465
MAIL_SMTP_SSL_ENABLE=true
MAIL_SMTP_STARTTLS_ENABLE=false
MAIL_ACCOUNT=no-reply@example.com
MAIL_AUTH_CODE=change-this-mail-authorization-code
DEFAULT_ADMIN_ENABLED=true
DEFAULT_ADMIN_USERNAME=admin
DEFAULT_ADMIN_PASSWORD=change-this-admin-password
DEFAULT_ADMIN_EMAIL=admin@example.com
APP_BRAND_NAME=My Application
```

Linux/macOS/Git Bash 临时加载配置：

```bash
source ./config_helper/config-env.sh
```

使用 `source` 后，环境变量会写入当前 Shell，可直接在同一终端启动项目。也可以执行 `bash ./config_helper/config-env.sh`
，脚本会进入一个包含这些变量的新 Shell。

Windows PowerShell 临时加载配置：

```powershell
.\config_helper\config-env.ps1
```

辅助脚本还支持永久配置模式：将 Bash 脚本中的 `MODE` 改为 `permanent`，或将 PowerShell 脚本中的 `$Mode` 改为 `Permanent`
后再执行。Bash 会写入 `PERSIST_FILE` 指定的 Shell 配置文件；PowerShell 会写入当前用户级环境变量。永久配置后需重新加载 Shell
配置或重新打开终端。

## 配置项说明

### 服务与数据库

| 变量          | 含义                                                            |
|---------------|-----------------------------------------------------------------|
| `SERVER_PORT` | 后端 HTTP 监听端口。使用 Nginx 或网关时通常只在内网暴露该端口。 |
| `DB_URL`      | MySQL JDBC 连接地址，包含主机、端口、数据库名及 JDBC 参数。     |
| `DB_USERNAME` | MySQL 用户名。生产环境建议使用权限受限的应用专用账号。          |
| `DB_PASSWORD` | MySQL 密码。                                                    |

### JWT 与 Cookie

| 变量                   | 含义                                                                                                        |
|------------------------|-------------------------------------------------------------------------------------------------------------|
| `JWT_SECRET`           | JWT 签名密钥，至少 32 个字符；生产环境应使用密码学安全的随机值。                                            |
| `JWT_COOKIE_SECURE`    | 是否仅通过 HTTPS 发送认证 Cookie。HTTPS 部署设为 `true`；仅本地 HTTP 调试时可设为 `false`。                 |
| `JWT_COOKIE_SAME_SITE` | Cookie 的 SameSite 策略。常见值为 `Strict`、`Lax` 或 `None`；使用 `None` 时通常还必须启用 HTTPS 和 Secure。 |

### Redis 与验证码限流

| 变量                                         | 含义                                                     |
|----------------------------------------------|----------------------------------------------------------|
| `AUTH_CACHE_TYPE`                            | 认证缓存实现。当前项目仅支持 `redis`。                   |
| `SPRING_DATA_REDIS_HOST`                     | Redis 主机名或 IP 地址。                                 |
| `SPRING_DATA_REDIS_PORT`                     | Redis 服务端口。                                         |
| `SPRING_DATA_REDIS_PASSWORD`                 | Redis 密码；仅在 Redis 确实未启用认证时才留空。          |
| `SPRING_DATA_REDIS_DATABASE`                 | Redis 逻辑数据库编号。应避免与其他应用混用。             |
| `AUTH_EMAIL_CODE_IP_SEND_WINDOW_SECONDS`     | 按客户端 IP 统计邮件验证码发送次数的时间窗口，单位为秒。 |
| `AUTH_EMAIL_CODE_MAX_IP_SEND_COUNT`          | 单个 IP 在上述时间窗口内允许发送验证码的最大次数。       |
| `AUTH_EMAIL_CODE_GLOBAL_SEND_WINDOW_SECONDS` | 全局验证码发送限流的统计窗口，单位为秒。                 |
| `AUTH_EMAIL_CODE_MAX_GLOBAL_SEND_COUNT`      | 整个应用在全局时间窗口内允许发送验证码的最大次数。       |

### 反向代理与业务范围

| 变量                         | 含义                                                                                     |
|------------------------------|------------------------------------------------------------------------------------------|
| `AUTH_TRUST_FORWARD_HEADERS` | 是否信任代理转发的客户端地址请求头。仅在应用位于受控反向代理后方时设为 `true`。          |
| `AUTH_TRUSTED_PROXIES`       | 允许信任的代理 IP 或网段列表。不要配置不受控制的公网地址；不启用转发头信任时可留空。     |
| `APPLICATION_ALLOWED_GRADES` | 允许报名的年级枚举，多个值以逗号分隔，例如 `YEAR_1,YEAR_2`。应填写项目支持的有效枚举值。 |

### 文件存储与上传

| 变量                                  | 含义                                                                                                                |
|---------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `APP_STORAGE_ROOT`                    | 上传文件的本地存储根目录。生产环境应使用有备份和持久化能力的独立目录。                                              |
| `APP_STORAGE_CHUNK_SIZE`              | 业务分片大小，同时决定上传模式。`0B` 表示只允许直传；大于零（如 `5MB`）表示启用分片上传并禁用直传。                 |
| `APP_STORAGE_CLEANUP_ENABLED`         | 是否启用临时上传会话和孤立文件的自动清理。                                                                          |
| `APP_STORAGE_TEMP_SESSION_TTL`        | 未完成分片上传会话的保留时长，支持 Spring `Duration` 格式，如 `24h`。                                               |
| `APP_STORAGE_ORPHAN_FILE_TTL`         | 孤立文件被清理前的保留时长。                                                                                        |
| `APP_STORAGE_ALLOWED_EXTENSIONS`      | 允许上传的文件扩展名白名单，以逗号分隔且不带点号。                                                                  |
| `APP_STORAGE_ALLOWED_CONTENT_TYPES`   | 允许上传的 MIME 类型白名单，以逗号分隔；应与扩展名白名单对应。                                                      |
| `TASK_ATTACHMENT_MAX_SIZE`            | 单个任务附件或任务提交附件的业务大小上限。                                                                          |
| `MULTIPART_MAX_FILE_SIZE`             | Servlet 层单个 multipart 文件的请求上限，不等同于业务分片大小。直传时应不小于附件业务上限；分片时应不小于单个分片。 |
| `MULTIPART_MAX_REQUEST_SIZE`          | Servlet 层整个 multipart 请求的上限，应略大于单文件或单分片上限，以容纳表单开销。                                   |
| `APP_AUDIT_MAJOR_EVENT_LOG_FILE_PATH` | 重大业务事件审计日志文件路径。生产环境应放在可持久化且访问权限受控的日志目录。                                      |

### 邮件服务

| 变量                        | 含义                                                                                      |
|-----------------------------|-------------------------------------------------------------------------------------------|
| `MAIL_SMTP_HOST`            | SMTP 服务器主机名。                                                                       |
| `MAIL_SMTP_PORT`            | SMTP 端口。常见的隐式 SSL 端口为 `465`，STARTTLS 端口为 `587`，具体以邮件服务商说明为准。 |
| `MAIL_SMTP_SSL_ENABLE`      | 是否启用 SMTP 隐式 SSL。使用端口 `465` 时通常设为 `true`。                                |
| `MAIL_SMTP_STARTTLS_ENABLE` | 是否启用 STARTTLS。使用端口 `587` 时通常设为 `true`，并将隐式 SSL 关闭。                  |
| `MAIL_ACCOUNT`              | 用于登录 SMTP 服务和发送邮件的账号。                                                      |
| `MAIL_AUTH_CODE`            | SMTP 密码或邮件服务商提供的客户端授权码。                                                 |

### 初始化与品牌

| 变量                     | 含义                                                                                 |
|--------------------------|--------------------------------------------------------------------------------------|
| `DEFAULT_ADMIN_ENABLED`  | 是否在首次部署且数据库中不存在管理员时自动创建初始管理员。已有管理员时不会重复创建。 |
| `DEFAULT_ADMIN_USERNAME` | 初始管理员用户名。                                                                   |
| `DEFAULT_ADMIN_PASSWORD` | 初始管理员密码。首次登录后应立即修改。                                               |
| `DEFAULT_ADMIN_EMAIL`    | 初始管理员邮箱。                                                                     |
| `APP_BRAND_NAME`         | 应用在邮件或界面中显示的品牌名称。                                                   |

### 辅助脚本自身配置

| 配置                             | 含义                                                   |
|----------------------------------|--------------------------------------------------------|
| Bash `MODE` / PowerShell `$Mode` | 选择临时或永久写入环境变量。建议本地开发使用临时模式。 |
| Bash `PERSIST_FILE`              | 永久模式写入的 Shell 配置文件，仅 Bash 脚本使用。      |

## 运行项目

确认 MySQL、Redis 均可连接，并在当前终端加载全部环境变量后，从项目根目录执行：

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS/Git Bash：

```bash
./mvnw spring-boot:run
```

首次运行会下载 Maven 依赖并执行 Flyway 数据库迁移。日志出现应用启动完成信息后，可通过 `http://localhost:<SERVER_PORT>`
访问后端。

也可以先构建 JAR 再运行：

```powershell
.\mvnw.cmd clean package
java -jar .\target\backend-1.0.0.jar
```

Linux/macOS 对应命令：

```bash
./mvnw clean package
java -jar ./target/backend-1.0.0.jar
```

## 常见启动问题

- MySQL 连接失败：检查 `DB_URL` 中的主机、端口和数据库名，并确认账号权限及网络访问策略。
- Redis 校验失败：检查 Redis 是否启动、密码是否正确，以及所选数据库编号是否可用。
- JWT 配置报错：确认 `JWT_SECRET` 长度至少为 32 个字符。
- 验证码邮件无法发送：核对 SMTP 地址、端口、SSL/STARTTLS 组合、账号和授权码。
- HTTP 本地调试无法保存登录状态：确认本地非 HTTPS 环境下 `JWT_COOKIE_SECURE=false`；生产环境应恢复为 `true`。
- 文件上传被拒绝：同时检查扩展名、MIME 类型、业务附件上限和 multipart 请求上限。
