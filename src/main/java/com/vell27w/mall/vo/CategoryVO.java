package com.vell27w.mall.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树节点 —— 分类接口的出参。
 *
 * ============================================================
 * 【树是在 Java 里拼出来的，不是数据库查出来的】
 * ============================================================
 * 数据库里 goods_category 是平铺的（每条只有一个 parentId），
 * 接口要返回的是嵌套的 JSON（children 套 children）。中间这步在服务层完成：
 *
 *   ① 一条 SELECT 把全表捞出来（几十行，很便宜），顺手按 rank 排好
 *   ② 用 Map<父id, 该父id下的子节点列表> 把它们归好组
 *   ③ 从「顶级节点」（parentId = 0）开始，递归把 children 挂上去
 *
 * 【为什么是一条 SQL 捞全表，而不是递归查询数据库？】
 *   全表几十行、一次查完，网络往返只有 1 次。
 *   如果每个节点都去查一次子节点，三层树就是 1 + N + N*M 次往返 ——
 *   这叫 N+1 查询问题，是面试常问的性能坑，也是 ORM 最容易踩的坑。
 *   分类这种「数据量小、结构固定」的场景，一次全捞在内存里拼是标准答案。
 *
 * 【改坏它会怎样？】
 * 如果 children 初始化成 null 而不是空列表，返回的 JSON 里叶子节点会是
 * "children": null。前端写 node.children.length 就会报 Cannot read
 * properties of null —— 而且只在「点到叶子分类」时才炸，很典型。
 * 所以这里直接给个空 ArrayList，前端不用做 null 判断。
 */
@Data
public class CategoryVO {

    /** 分类 id。前端点分类时把这个值作为 categoryId 传给商品列表接口 */
    private Long id;

    /** 父分类 id。顶级分类为 0 */
    private Long parentId;

    /** 分类名称 */
    private String categoryName;

    /**
     * 层级：1 / 2 / 3
     *
     * 接口返回它，是为了让前端能「按层渲染」而不必自己数深度。
     * 比如一级分类横向铺开、二级分类做侧边栏、三级分类做标签页。
     */
    private Integer categoryLevel;

    /**
     * 子分类。没有子分类时是【空数组】，不是 null。
     *
     * 【为什么这里不是 entity？】
     * 因为多了 children 这个字段 —— 数据库里没有这一列。
     * 一个类只要包含了「表里没有的字段」，它就不是 entity 了。
     */
    private List<CategoryVO> children = new ArrayList<>();

    /*
     * 【注意这里没有 categoryRank】
     * 排序值只在「拼树的时候用来排顺序」，排完就没用了。
     * 前端拿到的是已经排好的数组，不需要知道排序依据。
     * 少返回一个字段，就少一个「前端误以为它能改顺序」的误解。
     *
     * 同理也没有 isDeleted —— 被逻辑删除的分类根本不会出现在结果里，
     * 返回它等于告诉前端「我们用的是逻辑删除」，那是内部实现，不该漏出去。
     */
}
