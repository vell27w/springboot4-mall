package com.vell27w.mall.service;

import java.util.List;

import com.vell27w.mall.vo.CategoryVO;

/**
 * 商品分类业务接口。
 *
 * 【为什么要有接口？直接写一个类不行吗？】
 * 这是本项目的规范，不是必须。说实话，一个接口只有一个实现类，
 * 多一层间接确实有代价（改方法要改两个文件）。
 *
 * 保留它的理由：
 *   ① 分层清晰 —— 看 service 包就知道「这个系统能做什么」，
 *      不用点进每个实现类的内部
 *   ② 以后真要换实现（比如加个带缓存的实现类）时，
 *      Controller 一行都不用改
 *   ③ 参考项目 newbee-mall-api 也是这么分的，保持一致的代码风格
 *
 * 面试被问「接口是不是过度设计」时，答「单体项目里它的收益确实有限，
 * 主要是规范统一和降低后续替换成本」——比硬说「必须这么写」要诚实。
 */
public interface GoodsCategoryService {

    /**
     * 查完整的分类树（三级嵌套）。
     *
     * 返回的是【已经嵌套好】的结构，不是平铺列表 ——
     * 拼树在服务层完成，前端拿到直接就能渲染侧边栏。
     */
    List<CategoryVO> tree();
}
