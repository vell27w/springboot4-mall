package com.vell27w.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vell27w.mall.common.Result;
import com.vell27w.mall.service.GoodsCategoryService;
import com.vell27w.mall.vo.CategoryVO;

import java.util.List;

/**
 * 分类接口层。
 *
 * 【为什么分类和商品分成两个 Controller，不合成一个？】
 * 因为它们是【两个资源】，各有各的 URL 前缀：
 *      /categories/**  —— 分类
 *      /goods/**       —— 商品
 *
 * 合成一个类的话，@RequestMapping 就只能写公共前缀（比如 /mall），
 * 每个方法再各写各的完整路径 —— 参数校验、拦截器（以后要加登录拦截）
 * 想按资源分别配置时就绕不开了。
 *
 * 判断标准：**两个 Controller 的 URL 前缀是不是同一个资源？**
 * 不是就分开。这个划分方式后面加购物车、订单时会一直用。
 */
@RestController
@RequestMapping("/categories")
@Tag(name = "分类", description = "商品分类树。本项目分类只读，不做增删改（不做后台管理端）")
public class GoodsCategoryController {

    private final GoodsCategoryService goodsCategoryService;

    public GoodsCategoryController(GoodsCategoryService goodsCategoryService) {
        this.goodsCategoryService = goodsCategoryService;
    }

    /**
     * GET /categories/tree —— 一次返回完整的三级分类树。
     *
     * 【为什么不像商品那样做成分页的列表？】
     * 分类总量是几十条，一次全返回也就几 KB，前端拿到直接渲染侧边栏。
     * 分页对它是负担：侧边栏要的是完整结构，
     * 分页之后前端还得自己把多页数据拼起来 —— 纯属自找麻烦。
     *
     * 「要不要分页」的判断依据是【数据量级】，不是「接口规范要求分页」。
     * 面试被问到能说清这个就够了。
     *
     * 【为什么没有参数？】
     * 分类树永远返回全量（除了被逻辑删除的），
     * 没有筛选、没有排序参数 —— 排序规则写死在 SQL 里（rank 大的靠前）。
     * 参数越少越好：每多一个参数就多一种被人传错的可能。
     */
    @GetMapping("/tree")
    @Operation(summary = "查询分类树",
            description = "返回三级嵌套结构，叶子节点的 children 是空数组而不是 null")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(goodsCategoryService.tree());
    }
}
