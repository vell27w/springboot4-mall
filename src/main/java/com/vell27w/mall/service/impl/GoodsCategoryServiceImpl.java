package com.vell27w.mall.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.vell27w.mall.entity.GoodsCategory;
import com.vell27w.mall.mapper.GoodsCategoryMapper;
import com.vell27w.mall.service.GoodsCategoryService;
import com.vell27w.mall.vo.CategoryVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分类树业务实现。
 *
 * ============================================================
 * 【本类唯一值得细看的地方：树是怎么拼出来的】
 * ============================================================
 * 数据库里是平铺的（每条只有一个 parentId），接口要返回的是嵌套 JSON。
 * 拼法是三步，全部在内存里完成，【只查了一次库】：
 *
 *   ① 把 entity 转成 VO（顺便丢掉 rank / isDeleted 这些内部字段）
 *   ② 遍历一遍，按 parentId 归组：Map<父id, 该父id的子节点列表>
 *   ③ 再遍历一遍，把每组挂到对应父节点的 children 上
 *
 * 复杂度是 O(n)，n 是分类总数（几十条），可以忽略。
 *
 * 【为什么不这么写：边遍历边递归查数据库？】
 *   「遇到一个节点就去数据库查它的子节点」是最直觉的写法，也是错的：
 *   三层树会变成 1 + N + N×M 次数据库往返 —— 这叫 N+1 查询问题，
 *   是面试高频考点，也是 ORM 框架最容易踩的性能坑。
 *   分类这种「数据量小、结构固定」的数据，一次全捞进内存拼是标准答案。
 *
 * 【为什么不用递归函数拼？】
 *   也能写，但对三层固定结构来说，迭代版更好读、也不会栈溢出。
 *   递归的写法在「层数不确定」时才更值得 —— 本项目的层数是产品定死的三级。
 */
@Slf4j
@Service
public class GoodsCategoryServiceImpl implements GoodsCategoryService {

    /** 顶级分类的 parentId 约定值。数据库里是 0，不是 NULL */
    private static final long TOP_LEVEL_PARENT_ID = 0L;

    private final GoodsCategoryMapper goodsCategoryMapper;

    public GoodsCategoryServiceImpl(GoodsCategoryMapper goodsCategoryMapper) {
        this.goodsCategoryMapper = goodsCategoryMapper;
    }

    @Override
    public List<CategoryVO> tree() {
        // 已经按 level 升、rank 降、id 升 排好序（见 XML 里的 ORDER BY）。
        // 顺序在这里很重要：下面挂 children 时是【按列表顺序】追加的，
        // 所以同一父节点下的兄弟顺序，就是 SQL 里排好的那个顺序。
        List<GoodsCategory> rows = goodsCategoryMapper.findAllForTree();

        // ---- ① entity -> VO ----
        List<CategoryVO> nodes = new ArrayList<>(rows.size());
        for (GoodsCategory row : rows) {
            nodes.add(toVO(row));
        }

        // ---- ② 按 parentId 归组 ----
        // key = 父节点 id，value = 它的所有直接子节点（保持列表里的原顺序）
        Map<Long, List<CategoryVO>> childrenByParent = new HashMap<>();
        for (CategoryVO node : nodes) {
            childrenByParent
                    .computeIfAbsent(node.getParentId(), k -> new ArrayList<>())
                    .add(node);
        }

        // ---- ③ 把每组挂到对应的父节点上 ----
        for (CategoryVO node : nodes) {
            List<CategoryVO> raw = childrenByParent.get(node.getId());
            if (raw == null) {
                continue;   // 叶子节点，children 保持构造时那个空列表
            }

            /*
             * ============================================================
             * 【这三行是在防什么？—— 一个不建外键约束的代价】
             * ============================================================
             * goods_category 表【没有建外键约束】，所以数据库不会阻止
             * 「A 是 B 的父节点、B 又是 A 的父节点」这种脏数据进来。
             * （本项目不做后台，数据是初始化脚本写的，风险低；
             *   但只要有人手改过库，这种数据就可能存在。）
             *
             * 一旦成环，上面拼出来的对象图谱就成环了，
             * Jackson 序列化时会无限递归 —— 直接 StackOverflowError，接口 500。
             * 而且报错信息里完全看不出是数据的问题，会以为是代码写错了。
             *
             * 兜底办法很便宜：**子节点的层级必须正好等于父节点层级 + 1**。
             * 正常数据天然满足（三级树就是这么定义的），
             * 成环的数据必然不满足（A 和 B 同级，1 != 1+1），于是不会被挂上去，
             * 环就断了。
             *
             * ⚠️ 为什么是「丢日志 + 跳过」而不是「抛异常」？
             * 因为这是个公开的只读接口：一条脏分类不该让整个分类树打不开。
             * 少一个分类，页面还能用；整个接口 500，页面全白。
             * 但【必须打日志】—— 静默丢弃等于把数据问题藏起来，
             * 藏久了就没人知道库里脏了。
             * ============================================================
             */
            List<CategoryVO> children = new ArrayList<>(raw.size());
            for (CategoryVO candidate : raw) {
                if (Objects.equals(candidate.getCategoryLevel(), node.getCategoryLevel() + 1)) {
                    children.add(candidate);
                } else {
                    log.warn("分类层级异常，已跳过：id={}, name={}, level={}, 父节点 id={}, level={}",
                            candidate.getId(), candidate.getCategoryName(),
                            candidate.getCategoryLevel(), node.getId(), node.getCategoryLevel());
                }
            }
            node.setChildren(children);
        }

        // ---- 返回顶级节点 ----
        // 顶级节点的 parentId 都是 0，所以 directly 取这一组就是整棵树。
        // 用 getOrDefault 而不是 get：万一一个顶级分类都没有（空库、或者
        // 所有分类都被逻辑删除了），返回空列表而不是 null ——
        // 前端拿到 [] 能正常渲染「暂无分类」，拿到 null 会炸。
        return childrenByParent.getOrDefault(TOP_LEVEL_PARENT_ID, new ArrayList<>());
    }

    /**
     * entity -> VO。
     *
     * 【这里为什么是手写赋值，不用 BeanUtils.copyProperties？】
     *   1. 用反射的 copyProperties 慢（虽然在这个量级上无所谓），
     *      更重要的是它【靠字段名匹配】：哪天给 entity 加了字段、
     *      又恰好和 VO 同名，就会悄悄多copy一份出去，没人会发现。
     *   2. 手写的话，编译期就能发现字段名写错 —— 拼错一个字母直接编译不过。
     *      反射是在运行时才炸，甚至不炸（只是这个字段永远为 null）。
     *   3. 一共就几个字段，手写一行一个，一眼能看完。
     *
     * 【故意没有转的字段】
     *   categoryRank  —— 只在 SQL 里排序用，前端拿到的是排好的结果，不需要依据
     *   isDeleted     —— 被逻辑删除的分类根本查不出来，返回它等于
     *                    告诉前端「我们用的是逻辑删除」，那是内部实现
     *   createTime / updateTime —— 内部字段
     */
    private CategoryVO toVO(GoodsCategory row) {
        CategoryVO vo = new CategoryVO();
        vo.setId(row.getId());
        vo.setParentId(row.getParentId());
        vo.setCategoryName(row.getCategoryName());
        vo.setCategoryLevel(row.getCategoryLevel());
        return vo;
    }
}
