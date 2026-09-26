package com.vell27w.mall.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体 —— 和数据库 shop.goods_info 表一一对应。
 *
 * 【这个类只在「服务层内部」用，不直接返回给前端】——
 * 返回前端的是 GoodsListVO / GoodsDetailVO。原因见 GoodsListVO 类里的长注释。
 *
 * 【本题最值得记的两个字段】
 *
 * ① goodsDetailContent（商品详情富文本）
 *    列表页绝对不能查它。15 条商品就是 15 份富文本，
 *    列表接口的响应体会从几 KB 涨到几百 KB。
 *    这就是「列表单独做一个 VO」最实在的理由 —— 不是洁癖，是流量。
 *
 * ② goodsSellStatus（上架状态）
 *    值是 0-已上架 / 1-已下架，【0 才是上架】，反直觉，是从参考项目沿用的。
 *    写 SQL 判断时如果顺手写成 = 1，结果是「只查到了已下架的商品」——
 *    接口不报错，就是返回的东西不对，最难查。
 */
@Data
public class GoodsInfo {

    /** 主键，对应 BIGINT */
    private Long id;

    /** 商品名，对应 VARCHAR(200) */
    private String goodsName;

    /** 一句话简介，对应 VARCHAR(200)。列表页显示在商品名下面 */
    private String goodsIntro;

    /** 所属分类 id，对应 BIGINT。指向 goods_category.id，挂在叶子分类上 */
    private Long categoryId;

    /** 封面图 URL，对应 VARCHAR(200) */
    private String coverImg;

    /** 详情页富文本，对应 TEXT。⚠️ 列表页不要查这一列 */
    private String goodsDetailContent;

    /** 原价（划线价），对应 DECIMAL(10,2) */
    private BigDecimal originalPrice;

    /**
     * 实际售价，对应 DECIMAL(10,2)
     *
     * 金额一律 BigDecimal。这个项目没用「int 存分」那种做法
     * （把 ¥29.90 存成 2990），两种都是行业常见选择：
     *   int 存分 —— 运算全是整数，绝对不丢精度；代价是每个边界都要记得 ×100 / ÷100
     *   DECIMAL  —— 直观、和数据库类型一致；代价是 Java 侧必须用 BigDecimal 运算
     * 选了哪种就要贯彻到底，混用（一处 BigDecimal 一处 double）才是灾难。
     */
    private BigDecimal sellingPrice;

    /**
     * 库存，对应 INT
     *
     * 下单时会用「条件更新」扣它：UPDATE ... SET stock_num = stock_num - ? WHERE stock_num >= ?
     * 靠影响行数判断有没有扣成功 —— 这是防超卖的核心手段，到时再细讲。
     */
    private Integer stockNum;

    /** 标签，如「热卖」，对应 VARCHAR(20) */
    private String tag;

    /** 上架状态：0-已上架 1-已下架，对应 TINYINT。⚠️ 0 才是上架 */
    private Integer goodsSellStatus;

    /** 创建时间，对应 DATETIME */
    private LocalDateTime createTime;

    /** 更新时间，对应 DATETIME */
    private LocalDateTime updateTime;
}
