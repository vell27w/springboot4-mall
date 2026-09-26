package com.vell27w.mall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品详情 —— 详情接口的出参。
 *
 * ============================================================
 * 【为什么详情这个接口「不做 JOIN」】
 * ============================================================
 * 详情页经常要显示「所属分类」的名字。分类名在 goods_category 表里，
 * 要拿到它就得 JOIN 两张表。本项目【故意不 JOIN】，返回的是 categoryId。
 *
 * 理由不是偷懒，是两个具体的代价：
 *
 * ① 以后要给这个接口加缓存，而缓存的对象就是 GoodsDetailVO 这个整体。
 *    如果它由两张表拼成，「分类改名了，缓存里的商品详情要怎么办」就成了
 *    一个新的、独立的一致性问题 —— 缓存雪崩那些知识还没讲，
 *    先别自己造一个出来。
 *
 * ② 分类数据本来就很小、而且前端【已经完整拿过一次】了
 *    （分类树接口返回的就是全量），前端手上有一份 id -> 名字 的映射，
 *    自己能查出来。让服务端再拼一遍，是重复劳动。
 *
 * 这也是通用原则：**由调用方本地就能完成的换算，不要放到服务端做**。
 *
 * 【接口对「已下架 / 不存在」的处理】
 * 两种情况都返回 404（ResultCode.NOT_FOUND），不返回「商品已下架」这种提示。
 * 因为这是个公开接口 —— 下架的目的就是让它搜不到、点不进。
 * 如果返回一个带内容的「已下架」页面，等于变相承认这个商品存在过。
 * （内部管理后台就完全不同了，那种接口必须能看到下架商品。）
 *
 * 【改坏它会怎样？】
 * 如果在 SQL 里忘了加「只查上架」的条件，已下架商品能被直接访问到。
 * 这种 bug 在测试时很难发现 —— 你会拿着手边的 id 去调，那个商品多半是上架的。
 * 只有拿到一个已下架的 id 才会暴露。
 */
@Data
public class GoodsDetailVO {

    /** 商品 id */
    private Long id;

    /** 商品名 */
    private String goodsName;

    /** 一句话简介 */
    private String goodsIntro;

    /**
     * 所属分类 id（【不是】分类名，见类注释）
     *
     * 前端拿它去自己的分类树里查名字，顺便还能显示「数码家电 / 手机 / 智能手机」
     * 这种面包屑导航。
     */
    private Long categoryId;

    /** 封面图 URL */
    private String coverImg;

    /** 详情富文本。列表接口不返回它，只有这里返回 */
    private String goodsDetailContent;

    /** 原价（划线价） */
    private BigDecimal originalPrice;

    /** 实际售价 */
    private BigDecimal sellingPrice;

    /** 库存。前端用来显示「仅剩 N 件」或把「加入购物车」按钮置灰 */
    private Integer stockNum;

    /** 标签，如「热卖」 */
    private String tag;

    /*
     * 【这里没有 goodsSellStatus】
     * 因为详情接口只返回上架商品（见上面的类注释），
     * 能拿到响应就说明它是上架的，这个字段恒为 0，没必要返回。
     *
     * 【也没有 createTime / updateTime】
     * 内部字段。详情页不显示「这个商品是 2019 年录入的」。
     */
}
