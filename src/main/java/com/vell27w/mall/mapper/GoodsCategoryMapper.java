package com.vell27w.mall.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.vell27w.mall.entity.GoodsCategory;

import java.util.List;

/**
 * 商品分类数据访问接口。
 *
 * 【为什么这里只有「查」，一个写入方法都没有？】
 * 这是有意为之，不是漏写。
 *
 * 分类的增删改属于【后台管理端】的职能，而规划表里明确写了「不做后台管理端」——
 * 面经里没有一条要求它，做了会挤占主链路的时间。
 * 所以这个项目里的分类是「初始化数据」：由 sql/schema.sql 建好，
 * 之后不会有接口去改它。
 *
 * 面试如果问「那分类怎么维护」，标准答案就是这句 ——
 * 「本项目不做后台，分类是初始化数据，只读不写」。
 * 这个回答比硬凑一个后台要好：说清楚了范围，也说明白了自己知道边界在哪。
 *
 * （这也正好解释了为什么给 GoodsCategory 那个类里留了 isDeleted 字段 ——
 *   逻辑删除是表设计的通用做法，虽然本项目暂时没有地方去触发它。）
 */
@Mapper
public interface GoodsCategoryMapper {

    /**
     * 查出所有未删除的分类，并按「能直接拼树」的顺序排好。
     *
     * 返回的是 entity 而不是 VO —— 因为拼树要用到 parentId / categoryLevel /
     * categoryRank，而 CategoryVO 里【故意没有】categoryRank
     * （排序依据不该漏给前端）。所以拼树的原料只能用 entity。
     *
     * 调用方拿到之后，用 Map 归组 + 递归挂 children，一次查库搞定整棵树。
     * 详见 CategoryVO 类注释里关于 N+1 查询的说明。
     */
    List<GoodsCategory> findAllForTree();
}
