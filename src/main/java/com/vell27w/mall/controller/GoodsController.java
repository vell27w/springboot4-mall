package com.vell27w.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vell27w.mall.common.PageResult;
import com.vell27w.mall.common.Result;
import com.vell27w.mall.param.GoodsQueryParam;
import com.vell27w.mall.service.GoodsService;
import com.vell27w.mall.vo.GoodsDetailVO;
import com.vell27w.mall.vo.GoodsListVO;

/**
 * 商品接口层。只有一个列表和一个详情 —— 都是公开的只读接口。
 *
 * 【Controller 应该有多薄？】
 * 这一层只做三件事：接参数、调 Service、包返回值。
 * 任何 if/else 的业务判断都不该出现在这里 —— 一旦写进来，
 * 这个规则就只能被 HTTP 调用触发，定时任务、MQ 消费者想复用同一段逻辑
 * 时就得复制一遍。
 *
 * 自查方法：这个方法体里出现「业务规则」了吗？出现就说明该下沉到 Service。
 * 本类的两个方法都是「一行调 Service」，是标准的薄。
 */
@RestController
@RequestMapping("/goods")
@Tag(name = "商品", description = "商品列表与详情。只读，不做增删改（不做后台管理端）")
public class GoodsController {

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    /**
     * GET /goods/page?categoryId=111&keyword=手机&sort=price_desc&pageNum=1&pageSize=10
     *
     * 四个条件都可以不传，也可以任意组合：
     *   categoryId —— 不传 = 全部分类（注意：不传时排序用不上组合索引，见 XML 注释）
     *   keyword    —— 不传 = 不筛关键字（模糊匹配商品名，前置 % 用不了索引）
     *   sort       —— 不传 = 按 id 倒序；可选 price_asc / price_desc；非法值静默回落默认
     *   pageNum / pageSize —— 不传 = 1 / 10，pageSize 上限 100
     *
     * 【为什么查询参数用对象接，而不是一串 @RequestParam？】
     * 筛选条件只会越来越多。每加一个都要改方法签名，调用方也跟着改。
     * 用对象接，加字段不影响签名；而且页面参数和类字段是【按名字绑定】的，
     * 第 4 个参数以后靠 @RequestParam 一个个写，迟早会写漏一个。
     *
     * 【@Parameter 这里为什么一个都没加？】
     * 因为参数在一个对象里，Swagger 会把对象的字段展开成表单展示 ——
     * 每个字段的中文说明来自类字段上的 Javadoc 注释，
     * 那比在这里重复写一遍更好维护。想改说明就改 Param 类，只改一处。
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询商品列表",
            description = "支持分类、关键字、排序、分页四个条件任意组合。只返回已上架商品")
    public Result<PageResult<GoodsListVO>> page(GoodsQueryParam query) {
        return Result.ok(goodsService.page(query));
    }

    /**
     * GET /goods/1001
     *
     * 【为什么路径是 /goods/{id}，不带 /detail？】
     * 因为「按 id 拿一个商品」本身就是这个资源的代表操作，
     * REST 里用 GET /资源名/{id} 就够了。
     * 写成 /goods/detail?id=1001 的话，id 跑到查询参数里去了 ——
     * 那不是「查询条件」，是「标识」，该在路径上。
     *
     * 【查不到会怎样？】
     * 不存在和已下架都返回 code=404，不是 500 也不是 200。
     * 由 GlobalExceptionHandler 统一转，这里一行 if 都不用写。
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情",
            description = "商品不存在或已下架时返回 code=404。返回体里【不含】分类名，只有 categoryId")
    public Result<GoodsDetailVO> detail(
            @Parameter(description = "商品 id", example = "1001") @PathVariable Long id) {
        return Result.ok(goodsService.getDetail(id));
    }
}
