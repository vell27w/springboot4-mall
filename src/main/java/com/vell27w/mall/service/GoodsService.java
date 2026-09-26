package com.vell27w.mall.service;

import com.vell27w.mall.common.PageResult;
import com.vell27w.mall.param.GoodsQueryParam;
import com.vell27w.mall.vo.GoodsDetailVO;
import com.vell27w.mall.vo.GoodsListVO;

/**
 * 商品业务接口。
 *
 * 【注意这里一个「增删改」都没有】—— 和 GoodsCategoryService 同理。
 * 商品的增删改属于后台管理端，本项目不做。
 * 商品数据由 sql/schema.sql 初始化，之后只读。
 *
 * 这不是「还没写」，是范围划定 —— 见规划表里「明确不做」那张表。
 * 面试问「你的项目怎么没有后台」时，答「面经里没有一条要求它，
 * 我优先把主链路（下单、库存、缓存、MQ）做透」比硬做一个要好。
 */
public interface GoodsService {

    /**
     * 商品列表：分类 + 关键字 + 排序 + 分页，四个条件任意组合。
     *
     * 只返回已上架商品。查不到的字段见 GoodsListVO 的注释。
     */
    PageResult<GoodsListVO> page(GoodsQueryParam query);

    /**
     * 商品详情。
     *
     * ⚠️ 只返回【已上架】商品。已下架和不存在的商品都会抛 404，
     * 调用方拿到的一定是个有效商品，不用再判空。
     */
    GoodsDetailVO getDetail(Long id);
}
