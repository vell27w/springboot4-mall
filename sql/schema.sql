-- ============================================================================
--  建库建表 —— 当前进度：商品模块（分类树 / 列表 / 详情）已完成
-- ============================================================================
--  这个文件挂在容器的 /docker-entrypoint-initdb.d 上，只在
--  【数据卷第一次创建时】自动执行一次。
--
--  改完表结构想让它重新生效，必须先删卷再起（会清空数据）：
--      docker compose down -v && docker compose up -d
--  不想清数据，就把下面单独的 CREATE TABLE / INSERT 手动跑一遍。
--
--  ⚠️ 这是本项目最容易持续偷时间的坑：加了新表却忘了重建卷，
--     症状是程序报 Table 'shop.xxx' doesn't exist，而回头检查 SQL 怎么看都没问题。
--
--  手动跑（容器里）：
--      docker compose exec -T mysql mysql -uroot -proot < sql/schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS shop
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE shop;

-- ============================================================================
--  1. 商品分类表（三级树）
-- ============================================================================
--  树是怎么存的：每个节点只记自己的 parent_id，不存「完整路径」。
--  取整棵树 = 一次 SELECT 拿全部行，再在 Java 里按 parent_id 拼成树。
--  【为什么不存路径字符串】存了「1/11/111」确实能一条 SQL 查整棵子树，
--  但改父节点时要连带改所有后代的路径 —— 分类改动很少、树很小（几十行），
--  拼树的成本可以忽略，所以选简单的那种。
--
--  【category_level 是冗余字段】它完全能由 parent_id 往上推出来。
--  留着的原因：① 列表渲染、校验「最多三级」时不用递归；
--  ② 面试可以答「冗余字段的取舍 —— 读多写少、且这个字段几乎不变，值得冗余」。
--
--  注意：分类在表里是【平铺】的，不是嵌套的，level 只是标注不是结构。
-- ============================================================================

CREATE TABLE IF NOT EXISTS goods_category (
  id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类 id',
  category_level TINYINT     NOT NULL DEFAULT 1      COMMENT '层级：1-一级 2-二级 3-三级（冗余字段，可由 parent_id 推出）',
  parent_id      BIGINT      NOT NULL DEFAULT 0      COMMENT '父分类 id，顶级分类固定为 0',
  category_name  VARCHAR(50) NOT NULL                COMMENT '分类名称',
  category_rank  INT         NOT NULL DEFAULT 0      COMMENT '排序值，【越大越靠前】',
  is_deleted     TINYINT     NOT NULL DEFAULT 0      COMMENT '删除标识：0-未删除 1-已删除（逻辑删除，不真删）',
  create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP        COMMENT '更新时间，改行时数据库自动维护',
  PRIMARY KEY (id),
  KEY idx_parent_id (parent_id)   -- 按父分类找子分类时用
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================================================
--  2. 商品表
-- ============================================================================
--  【和参考项目不同的几处，都是故意的】
--
--  ① 价格用 DECIMAL(10,2)，不用 int。
--     参考项目 newbee 的 selling_price 是 int，表示不了 ¥29.90。
--     另一种行业做法是「int 存分」（29.90 存 2990），好处是运算全是整数、绝对不丢精度，
--     代价是每个边界都要记得 ×100 / ÷100。本项目选 DECIMAL。
--
--  ② goods_sell_status：0-已上架 1-已下架。
--     ⚠️ 这个约定反直觉（0 才是上架），是从参考项目沿用的。
--     改成 1-上架更符合直觉，但那要连带改所有 SQL 里的判断，
--     所以这里选择「保持一致 + 注释写死」，你写代码时如果看到 0 不要以为是 bug。
--
--  ③ 详情内容 goods_detail_content 是 TEXT，单独一列。
--     列表页【绝对不要 SELECT 它】—— 15 条商品就是 15 份富文本，
--     列表接口的响应体会瞬间变成几百 KB。列表查什么列，写 Mapper 时要说清楚。
-- ============================================================================

CREATE TABLE IF NOT EXISTS goods_info (
  id                   BIGINT        NOT NULL AUTO_INCREMENT     COMMENT '商品 id',
  goods_name           VARCHAR(200)  NOT NULL                    COMMENT '商品名',
  goods_intro          VARCHAR(200)  NOT NULL DEFAULT ''         COMMENT '一句话简介，列表页显示在商品名下面',
  category_id          BIGINT        NOT NULL DEFAULT 0          COMMENT '所属分类 id -> goods_category.id（挂在叶子分类上）',
  cover_img            VARCHAR(200)  NOT NULL DEFAULT ''         COMMENT '列表页封面图 URL',
  goods_detail_content TEXT          NULL                        COMMENT '详情页富文本，列表页不查它',
  original_price       DECIMAL(10,2) NOT NULL DEFAULT 0.00       COMMENT '原价（划线价）',
  selling_price        DECIMAL(10,2) NOT NULL DEFAULT 0.00       COMMENT '实际售价，列表页可按它排序',
  stock_num            INT           NOT NULL DEFAULT 0          COMMENT '库存，下单会用条件更新扣它',
  tag                  VARCHAR(20)   NOT NULL DEFAULT ''         COMMENT '标签，如「热卖」',
  goods_sell_status    TINYINT       NOT NULL DEFAULT 0          COMMENT '上架状态：0-已上架 1-已下架（注意 0 才是上架）',
  create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP        COMMENT '更新时间',
  PRIMARY KEY (id),

  -- ==========================================================================
  --  【面试重点：这个组合索引为什么是三列、为什么是这个顺序】
  -- ==========================================================================
  --  列表页的 SQL 长这样：
  --      SELECT ... FROM goods_info
  --      WHERE category_id = ? AND goods_sell_status = 0
  --      ORDER BY selling_price DESC
  --      LIMIT 0, 10;
  --
  --  建索引的口诀：**等值条件列在前，排序列在后**。所以顺序是
  --      (category_id, goods_sell_status, selling_price)
  --
  --  【最左前缀】这个索引能被用到几种程度：
  --      只查 category_id                      -> 用得上（用到第 1 列）
  --      category_id + goods_sell_status       -> 用得上（用前 2 列）
  --      三列全用上                             -> 用得上，且【排序也走索引，不用 filesort】
  --      只查 goods_sell_status（跳过第 1 列）  -> 用不上（不满足最左前缀）
  --
  --  【如果把 selling_price 放第二位会怎样】
  --      索引变成 (category_id, selling_price, goods_sell_status) 时，
  --      等值条件 goods_sell_status 被排序列隔在中间，它就用不上索引了 ——
  --      MySQL 只能把 category_id 匹配到的行全捞出来，再自己排序（Extra 里出现 filesort）。
  --      这就是「索引列顺序不是随便排的」的具体含义。
  --
  --  【这个索引救不了的场景】
  --      ORDER BY 换成 id DESC —— 索引里没有 id 的排序位置，照样 filesort。
  --      所以「排序字段能不能复用索引」取决于索引里有没有它。
  --
  --  【还有一件事：回表】
  --      列表页 SELECT 的列很多（名字、图、价格…），索引里只有 3 列，
  --      所以命中索引后还要拿主键回聚簇索引取整行 —— 这叫回表。
  --      想避免就得建「覆盖索引」（把要查的列都放进索引），
  --      代价是索引变大、写入变慢。几十条数据的演示项目不需要，但面试会问。
  -- ==========================================================================
  KEY idx_category_status_price (category_id, goods_sell_status, selling_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ============================================================================
--  【为什么商品名 goods_name 上没有索引】
--  关键字搜索是 WHERE goods_name LIKE '%键盘%'。
--  前面带 % 的模糊匹配没法用 B+Tree 索引定位（不知道从哪个前缀开始），只能全表扫。
--  真要做搜索得换方案（全文索引 / ES），本项目明确不做 —— 见规划表「明确不做」。
--  ============================================================================


-- ============================================================================
--  3. 测试数据
-- ============================================================================
--  用 INSERT IGNORE + 写死主键：这个文件被重复执行时不会报主键冲突。
--  代价是【改不了已存在的行】—— 想改数据就先把那一行删掉再跑，或者 down -v 重来。
-- ============================================================================

-- ---- 分类树：2 个一级 / 6 个二级 / 7 个三级 ----
-- 主键是手写的，规律是 1 -> 11 -> 111，一眼能看出父子关系（只是好读，不代表结构）
INSERT IGNORE INTO goods_category
  (id, category_level, parent_id, category_name, category_rank) VALUES
  -- 一级
  (1,  1, 0,   '数码家电',     100),
  (2,  1, 0,   '服饰鞋包',      99),
  -- 二级：数码家电下
  (11, 2, 1,   '手机',          10),
  (12, 2, 1,   '电脑',           9),
  (13, 2, 1,   '影音',           8),
  -- 二级：服饰鞋包下
  (21, 2, 2,   '男装',          10),
  (22, 2, 2,   '女装',           9),
  (23, 2, 2,   '鞋靴',           8),
  -- 三级：手机下
  (111,3, 11,  '智能手机',       0),
  (112,3, 11,  '手机配件',       0),
  -- 三级：电脑下
  (121,3, 12,  '笔记本',         0),
  (122,3, 12,  '显示器',         0),
  -- 三级：男装下
  (211,3, 21,  '上衣',           0),
  -- 三级：女装下
  (221,3, 22,  '连衣裙',         0),
  -- 三级：鞋靴下
  (231,3, 23,  '运动鞋',         0);

-- ---- 商品 ----
-- 故意埋了几种情况，方便把列表接口的每种筛选/排序都试出来：
--   * 同分类下价格有高有低    -> 验价格排序
--   * 有 2 条是 1（已下架）   -> 验「只查上架」有没有生效
--   * 有 1 条 stock_num = 0   -> 下单时用它验「库存不足」
--   * 分类分布在多个叶子上    -> 验分类筛选
INSERT IGNORE INTO goods_info
  (id, goods_name, goods_intro, category_id, cover_img,
   goods_detail_content, original_price, selling_price, stock_num, tag, goods_sell_status) VALUES
  -- 智能手机 (111)
  (1001, '智能手机 Pro 12',      '6.7 英寸 / 256G',        111, '/img/1001.jpg', '<p>旗舰机型</p>',  4999.00, 4599.00,  120, '热卖', 0),
  (1002, '智能手机 Lite',        '6.1 英寸 / 128G',        111, '/img/1002.jpg', '<p>轻旗舰</p>',    2999.00, 2799.00,   80, '',     0),
  (1003, '智能手机 青春版',      '5.8 英寸 / 64G',         111, '/img/1003.jpg', '<p>入门款</p>',    1599.00, 1399.00,  200, '',     0),
  -- 手机配件 (112)
  (1004, '65W 氮化镓充电器',     '双口 / 兼容笔记本',       112, '/img/1004.jpg', '<p>小巧</p>',       199.00,  149.00,  500, '',     0),
  (1005, '磁吸手机壳',           '透明防摔',               112, '/img/1005.jpg', '<p>防指纹</p>',      59.00,   39.90,  800, '',     0),
  -- 笔记本 (121)
  (1006, '轻薄笔记本 14 英寸',   'R7 / 16G / 512G',        121, '/img/1006.jpg', '<p>办公本</p>',    5499.00, 4999.00,   40, '新品', 0),
  (1007, '游戏本 16 英寸',       'i7 / 32G / 1T / 独显',    121, '/img/1007.jpg', '<p>高刷屏</p>',    9999.00, 9299.00,   15, '',     0),
  -- 显示器 (122)
  (1008, '27 英寸 2K 显示器',    'IPS / 75Hz',             122, '/img/1008.jpg', '<p>护眼</p>',      1299.00, 1099.00,   60, '',     0),
  (1009, '34 英寸带鱼屏',        '准 4K / 144Hz',          122, '/img/1009.jpg', '<p>曲面</p>',      3999.00, 3599.00,    8, '',     0),
  -- 男装上衣 (211)
  (1010, '纯棉圆领 T 恤',        '宽松 / 三色可选',         211, '/img/1010.jpg', '<p>透气</p>',        99.00,   69.00, 1000, '',     0),
  (1011, '美式复古卫衣',         '加绒 / 落肩',            211, '/img/1011.jpg', '<p>厚实</p>',       299.00,  229.00,  300, '热卖', 0),
  -- 女装连衣裙 (221)
  (1012, '法式碎花连衣裙',       '显瘦 / 收腰',            221, '/img/1012.jpg', '<p>垂感好</p>',     399.00,  319.00,  150, '',     0),
  (1013, '针织开衫连衣裙',       '两件套',                 221, '/img/1013.jpg', '<p>秋冬</p>',       499.00,  429.00,  100, '',     1),
  -- 运动鞋 (231)
  (1014, '轻量跑鞋',             '缓震 / 透气网面',         231, '/img/1014.jpg', '<p>日常跑</p>',     599.00,  479.00,  220, '',     0),
  (1015, '复古板鞋',             '百搭 / 男女同款',         231, '/img/1015.jpg', '<p>经典</p>',       459.00,  399.00,    0, '',     1);

-- ============================================================================
--  4. 改造前那张 product 表 —— 已删除
-- ============================================================================
--  它的 Java 全套（entity/Product、ProductMapper、ProductService、
--  ProductController、两个 Param）连同这张表一起删掉了。
--  原因：那是最初用来练「单表 CRUD 跑通一条链路」的脚手架，
--  被 goods_category + goods_info 完全取代。留着会有人对着两套商品接口问哪套是准的。
--
--  ⚠️ 本文件里删掉 CREATE TABLE，【不会】自动删掉已有容器里的那张表 ——
--  schema.sql 挂在 /docker-entrypoint-initdb.d，只在数据目录为空时执行一次。
--  已经起着的那套环境要手动删：
--      docker exec -it mall-mysql mysql -uroot -proot -e "DROP TABLE IF EXISTS shop.product;"
--  或者干脆 `docker compose down -v && docker compose up -d` 重建（数据全部按本文件重来）。
--  只删 Java 代码不删表，项目照样跑 —— 只是库里留着一张没人用的表。
-- ============================================================================
