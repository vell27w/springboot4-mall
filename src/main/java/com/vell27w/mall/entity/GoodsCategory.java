package com.vell27w.mall.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类实体 —— 和数据库 shop.goods_category 表一一对应。
 *
 * 【这张表存的是「平铺的树」】面试常见问法。
 * 每条记录只知道自己的 parentId，不知道自己是第几层之下的谁。
 * 整棵树的形状是「读出来之后在内存里拼」出来的，数据库里没有树结构。
 *
 * 对比另一种做法（存路径，如 path = "1/11/111"）：
 *   好处 —— 一条 SQL 就能查整棵子树（WHERE path LIKE '1/11/%'）。
 *   代价 —— 改一个节点的父级，要连带改它所有后代的 path。分类改动极少、
 *          全表也就几十行，所以本项目选简单的那种。
 * 面试答到「改动频率 vs 查询频率」这个取舍就够了。
 *
 * 【categoryLevel 是冗余字段】它完全能顺着 parentId 往上推出来。
 * 冗余的理由：① 校验「最多三级」、前端按层渲染时不用递归；
 * ② 这个字段几乎不变，读远多于写 —— 值得冗余。
 * 反例：如果把「该分类下有几个商品」也冗余进来，那就不能这么随便了，
 * 因为商品一变动就得同步更新，属于典型的「冗余带来的写放大」。
 */
@Data
public class GoodsCategory {

    /** 主键，对应 BIGINT */
    private Long id;

    /**
     * 层级：1-一级 2-二级 3-三级，对应 TINYINT
     *
     * 【为什么用 Integer 而不是 int？】
     * 用包装类型，null 才能表示「数据库里是 NULL / 这个字段没查」。
     * 基本类型 int 的默认值是 0，会把「没查到」和「值是 0」混成一样。
     */
    private Integer categoryLevel;

    /** 父分类 id，对应 BIGINT。顶级分类固定为 0（不是 NULL，见下方说明） */
    private Long parentId;

    /** 分类名称，对应 VARCHAR(50) */
    private String categoryName;

    /**
     * 排序值，对应 INT。【越大越靠前】，这是参考项目的约定。
     *
     * 注意这是个反直觉的约定：数值大的排在前面。
     * 所以 SQL 里是 ORDER BY category_rank DESC。
     * 「越大越靠前」和「越小越靠前」两种都有项目在用，
     * 看代码时一定要先确认约定，凭直觉猜必错。
     */
    private Integer categoryRank;

    /**
     * 逻辑删除标识：0-未删除 1-已删除，对应 TINYINT
     *
     * 【为什么是逻辑删除而不是 DELETE？】
     * 商品还挂在分类上的时候直接 DELETE 分类，那些商品就变成「孤儿」——
     * category_id 指向一个不存在的分类，页面上分类名是空的。
     * 逻辑删除让「删掉的分类」还能被查到名字，历史订单也能正常展示。
     *
     * 【改坏它会怎样？】
     * 查询忘加 is_deleted = 0，删掉的分类就会重新出现在分类树里。
     * 因为删除时行还在，测试时删一个再看列表「它怎么还在」——
     * 这类 bug 只在「删过东西之后」才出现。
     */
    private Integer isDeleted;

    /** 创建时间，对应 DATETIME */
    private LocalDateTime createTime;

    /** 更新时间，对应 DATETIME（数据库里配了 ON UPDATE CURRENT_TIMESTAMP 自动维护） */
    private LocalDateTime updateTime;
}
