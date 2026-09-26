# springboot4-mall

单体电商后端。重点不在功能堆得多，而在**高并发下最容易写错的那几段**：
库存扣减、缓存与数据库的一致性、订单超时关单。

**当前进度**：商品模块（分类树 / 列表 / 详情）已完成，下一步用户与登录鉴权。
代码注释里写了每个决策的理由和「改坏它会怎样」，是这份 README 的延伸。

## 技术栈

| | |
|---|---|
| 运行时 | Java 21（Temurin 21.0.12.1） |
| 框架 | Spring Boot 4.0.8 / Spring Framework 7.0.9 |
| 持久层 | MyBatis 4.0.1 + MySQL 8.0 |
| 中间件 | Redis 7、RabbitMQ 4（Docker Compose 起） |
| 接口文档 | springdoc-openapi 3.0.3（Swagger UI） |
| 链路追踪 | 手写 `TraceIdFilter` + SLF4J MDC，不引 Micrometer Tracing |

## 跑起来

需要 Docker Desktop。

```bash
docker compose up -d            # MySQL:3307 / Redis:6379 / RabbitMQ:5672（管理台 15672）
./mvnw spring-boot:run
```

- Swagger UI — http://localhost:8080/swagger-ui/index.html
- 健康检查 — http://localhost:8080/actuator/health

> ⚠️ `sql/schema.sql` 挂在 `docker-entrypoint-initdb.d` 上，**只在数据卷第一次创建时执行**。
> 改了表结构必须 `docker compose down -v` 重来（会清空数据），否则新表不会生效。

MySQL 用 **3307** 而不是 3306，是为了避开本机已装的 MySQL。容器内仍是 3306。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/categories/tree` | 商品分类树（三级；一次查全表，在内存里按 `parent_id` 拼） |
| GET | `/goods/page` | 商品列表：分类筛选 / 关键字 / 价格排序 / 分页 |
| GET | `/goods/{id}` | 商品详情 |
| GET | `/actuator/health` | 各组件健康状态 |

统一返回体 `Result<T>`。**HTTP 状态码只表达传输层结果**（正常 200、找不到 404），
业务错误码放在响应体里 —— 这个取舍和理由写在 `common/Result` 与 `common/ResultCode` 的注释里。

## 几个设计上的取舍

- **组合索引 `(category_id, goods_sell_status, selling_price)`** —— 等值条件列在前、排序列在后，
  这样带分类查列表时 `ORDER BY selling_price` 也能顺着索引走，不用 filesort。
  把 `selling_price` 挪到第二位就会退化成 filesort，`sql/schema.sql` 里写了完整对照。
- **列表接口不查 `goods_detail_content`** —— TEXT 富文本一页 10 条能让响应体涨到几百 KB，
  而列表页一个字都不显示它。列表和详情的 SELECT 列表是分开写的，不复用。
- **关键字搜索用 `LIKE CONCAT('%', #{keyword}, '%')`** —— 参数化而非字符串拼接；
  注释里也说明了为什么这种前缀带 `%` 的匹配用不上 B+Tree 索引。
- **动态排序走 XML 里的 `<choose>` 白名单**，不拼 `${}` —— `ORDER BY` 后面不能用 `#{}`
  占位符，而拼字符串就是注入点，所以改成在白名单里选。
- **详情接口故意不做 JOIN**（不返回分类名）—— 为后面的缓存留一个干净的缓存对象，
  免得「分类改名了缓存怎么办」变成额外的一致性问题。

## 目录结构

```
src/main/java/com/vell27w/mall/
├── common/       统一返回体 Result、错误码、分页基类
├── config/       全局异常处理、OpenAPI 配置、TraceIdFilter
├── controller/   接口层（薄，只做参数校验和转发）
├── service/      业务逻辑，impl/ 是实现
├── mapper/       MyBatis 接口，SQL 在 resources/mapper/*.xml
├── entity/       和表一一对应的对象
├── param/        入参对象（承接查询条件）
└── vo/           出参对象（和接口响应严格对应，不直接返回 entity）

sql/schema.sql    建库建表 + 索引 + 测试数据（容器首次启动时自动执行）
docker-compose.yml  MySQL / Redis / RabbitMQ
```

入参和出参都不复用 `entity` —— 这样「列表接口该查哪些列」是一个能被看见的决定，
而不是随手把整张表丢出去。

## 后面要做的

按顺序：

用户与登录 → 购物车与地址 → **下单与库存（防超卖 / 事务 / 状态机）**
→ Redis 缓存 → **RabbitMQ 超时关单** → 压测与收尾。

下单防超卖和超时关单是这个项目里最值得讲的两块，也是简历上的两条主线。
