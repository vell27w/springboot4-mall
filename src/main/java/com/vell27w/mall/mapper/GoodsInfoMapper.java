package com.vell27w.mall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.vell27w.mall.param.GoodsQueryParam;
import com.vell27w.mall.vo.GoodsDetailVO;
import com.vell27w.mall.vo.GoodsListVO;

import java.util.List;

/**
 * 商品数据访问接口。
 *
 * ============================================================
 * 【为什么这里返回的是 VO，不是 entity？】
 * ============================================================
 * 按严格的 MVC 分层，DAO 应该只碰 entity，VO 的转换归 service 做。
 * 本项目【故意打破】这条，因为这里恰好有个很实在的理由：
 *
 * 列表接口要的字段比 entity 少得多 —— 尤其是那个 TEXT 类型的
 * goodsDetailContent（商品详情富文本）必须【在 SQL 里就不查】。
 * 查出来再丢掉，省不了数据库到应用这一段网络传输，等于白做。
 *
 * 如果这里返回 GoodsInfo，SQL 就得查全字段，或者查一半、
 * 让另一半字段恒为 null —— 后者更糟：调用方看到 null 会以为「这个商品没详情」，
 * 而真相是「根本没查它」。用 VO 接，字段列表和 SQL 的 SELECT 列表一一对应，
 * 少一个字段一眼就能看出来。
 *
 * 【面试怎么讲这个取舍】
 * 答「为了少查大字段，让 SQL 的字段列表和 VO 严格对应」，
 * 比背「DAO 层只能返回 entity」要站得住脚 —— 分层是手段，不是目的。
 */
@Mapper
public interface GoodsInfoMapper {

    /**
     * 按条件分页查询商品列表。
     *
     * ⚠️ 只返回【已上架】商品 —— 上架状态写死在 XML 的条件片段里，
     * 调用方不需要（也不能）传这个条件。公开的列表接口永远只该看到上架商品。
     */
    List<GoodsListVO> findPage(@Param("query") GoodsQueryParam query);

    /**
     * 按同样的条件统计总数，供分页算总页数。
     *
     * 【必须和 findPage 共用同一段条件】否则会出现
     * 「列表里只有 3 条，但总页数说有 10 页」这种对不上的情况 ——
     * 前端翻到第 4 页发现是空的。所以条件抽成了 XML 里的 queryCondition 片段。
     */
    long countByCondition(@Param("query") GoodsQueryParam query);

    /**
     * 按 id 查商品详情。
     *
     * ⚠️ 方法名里的 OnSale 是【筛选条件的一部分，不是修饰词】：
     * 它只查已上架商品。已下架和不存在的商品，这里都返回 null，
     * service 层拿到 null 统一抛 404。
     *
     * 把「只查上架」做进 SQL，而不是「查出来再在 Java 里判断 status」——
     * 后者会让已下架商品的数据先被装载进内存，多一步没必要的开销，
     * 而且将来加了缓存，很容易把已下架商品缓存进去。
     * 能在数据库层面挡掉的，就别放到应用层挡。
     */
    GoodsDetailVO findOnSaleById(@Param("id") Long id);
}
