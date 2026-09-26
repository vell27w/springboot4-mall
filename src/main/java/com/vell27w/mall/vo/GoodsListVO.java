package com.vell27w.mall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表项 —— 列表接口的出参。
 *
 * ============================================================
 * 【这个类的存在理由就一条：不返回 goodsDetailContent】
 * ============================================================
 * 商品详情是富文本，一条几百字到几千字很常见。
 * 列表一页 10 条，10 份富文本就是几百 KB —— 而列表页【一个字都不显示它们】。
 * 用户点进详情页才需要。
 *
 * 所以列表接口的 SQL 里就不要 SELECT 那一列（见 GoodsInfoMapper.xml 的 baseColumns），
 * 而不是「查出来再丢掉」—— 那种做法省不了数据库到应用的这段传输。
 *
 * 【面试怎么答「列表接口怎么优化」】
 *   ① 只查要显示的列，大字段（TEXT/BLOB）一律不带
 *   ② 分页限制 pageSize 上限（PageQuery 里做了）
 *   ③ 排序字段要能走索引，避免 filesort（这就是 schema.sql 里那个组合索引的意义）
 *   ④ 再往上才是加缓存
 * 前三条不花钱、立刻见效，比直接上缓存靠谱 —— 缓存是最后一步，不是第一步。
 *
 * 【改坏它会怎样？】
 * 图省事直接返回 GoodsInfo，前端会拿到一堆它不用的字段。
 * 更糟的是以后给 goods_info 加了「成本价」这种内部字段，
 * 它会因为「反正在 entity 里」而自动泄露出去 —— 没人会去检查一个没动过的接口。
 */
@Data
public class GoodsListVO {

    /** 商品 id。点进详情页时用它拼详情接口的地址 */
    private Long id;

    /** 商品名 */
    private String goodsName;

    /** 一句话简介，显示在商品名下面 */
    private String goodsIntro;

    /** 封面图 URL */
    private String coverImg;

    /**
     * 原价（划线价）
     *
     * 详情页要用来做「原价 ￥4999」划掉、旁边写现价 ￥4599 的效果。
     * 列表页也常这么显示，所以这里留着。
     */
    private BigDecimal originalPrice;

    /** 实际售价 */
    private BigDecimal sellingPrice;

    /** 标签，如「热卖」。为空字符串时前端不显示那个角标 */
    private String tag;

    /*
     * 【这里没有的几个字段，每个都是有意的】
     *
     * goodsDetailContent —— 见上面的长注释，本类存在的首要理由
     * stockNum           —— 列表页不显示库存（下单时才关心够不够）
     * categoryId         —— 列表页已经在某个分类里了，不需要再告诉它分类是谁
     * goodsSellStatus    —— 列表接口只查上架的，这个字段恒等于 0，返回它没意义
     * createTime/updateTime —— 内部字段，公开接口不暴露
     *
     * 判断一个字段该不该出现在 VO 里的通用问法：
     *   「调用方拿到它之后要做什么？」答不上来就不该有。
     */
}
