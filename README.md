# AuthServer

基于 Java 21、纯 Kotlin、Ktor CIO、JetBrains Exposed 和
`kotlinx.serialization` 的认证服务。生产数据库仅支持 MariaDB；H2 只用于自动化测试。

## 配置

将 `config_sample.json` 复制为工作目录下的 `config.json`：

```json
{
  "ip": "127.0.0.1",
  "port": 3306,
  "username": "authserver",
  "password": "change-me",
  "database": "qn_auth",
  "maximumPoolSize": 10
}
```

`database` 默认为 `qn_auth`，`maximumPoolSize` 默认为 `10`，因此旧的四字段配置仍可读取。
首次启动时如果管理员表为空，服务会生成初始 token 并写入日志。生产部署建议显式提供：

```text
AUTH_INITIAL_ADMIN_TOKEN=<仅含 0-9、A-Z、a-z、_ 的 token>
```

## 数据库迁移

启动时由 Exposed 自动创建并校验以下新 schema：

- `auth_users`
- `auth_admins`
- `user_history`
- `card_events`
- `auth_schema_versions`
- `auth_schema_lock`

旧版完整的 `user`、`admin`、`log`、`card` schema 会被识别并迁移：数据分批复制，管理员
token 转换为唯一 SHA-256 digest；旧 MariaDB 的数值别名和 `DATETIME` 精度差异通过 Exposed
typed projection 读取，目标数据逐字段回读、总数和新 schema 复核通过后才记录版本。版本记录会保留
`legacy_cleanup_pending` 状态，只有已验证迁移的崩溃恢复流程才能继续清理旧表；迁移完成后重新出现的
同名表会被拒绝而不会静默删除。已废弃且没有 API 的 `batchMsg` 表不会被修改或删除。

迁移会拒绝 partial legacy schema、非法或重复 token、future version 和与已记录版本不一致的
schema，不会自动“修好”未知结构。MariaDB DDL 会隐式提交，而本项目按要求不使用 advisory-lock SQL，
因此首次迁移或版本升级时应只启动一个服务实例。数据库账号需要迁移所需的 DDL 与 DML 权限。

## 构建与运行

```powershell
.\gradlew.bat --no-daemon clean test verifyFatJar
java -jar build\libs\authserver-0.0.1-all-optimized.jar
```

服务监听 `10810`。optimized fat JAR 包含 MariaDB 驱动，不包含 H2、Fastjson、Gson、Netty 或
R8 本身。

## API 契约

全部业务 API 同时支持无前缀和 `/qa` 前缀，例如 `/user` 与 `/qa/user`。请求体直接按 JSON
文本解析，不强制要求 `Content-Type: application/json`，未知字段会被忽略。HTTP status 始终等于
响应体的 `code`。

| Method | Path | 请求字段 |
| --- | --- | --- |
| `GET` | `/`、`/qa` | 健康信息，无 JSON body |
| `POST` | `/user` | `uin`、可选 `status`、`token`、`reason` |
| `POST` | `/user/query` | `uin` |
| `DELETE` | `/user` | `uin`、`token`、`reason` |
| `GET` | `/user/history` | `uin`、`token`（JSON body） |
| `POST` | `/admin` | `desttoken`、`nickname`、可选 `reason`、`token` |
| `DELETE` | `/admin` | `desttoken`、`token` |
| `POST` | `/statistics/card/send` | `uin`、`msg` |

`uin` 与 `status` 接受 JSON number 或十进制字符串；缺失或 `null` 的 `status` 按 `0` 处理。
用户不存在时查询仍返回 `200`，其中 `status=0`、`reason=""`、`lastUpdate=""`。删除不存在的用户
返回 `200` 且不会写入虚假 history。history 数组中包括 `id`、`uin`、`operator`、`operation`、
`changes`、`reason`、`date`，所有字段均为 JSON string，时间格式为 `yyyy-MM-dd HH:mm:ss`。
