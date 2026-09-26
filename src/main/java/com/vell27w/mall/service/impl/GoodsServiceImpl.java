package com.vell27w.mall.service.impl;

import org.springframework.stereotype.Service;

import com.vell27w.mall.common.BusinessException;
import com.vell27w.mall.common.PageResult;
import com.vell27w.mall.common.ResultCode;
import com.vell27w.mall.mapper.GoodsInfoMapper;
import com.vell27w.mall.param.GoodsQueryParam;
import com.vell27w.mall.service.GoodsService;
import com.vell27w.mall.vo.GoodsDetailVO;
import com.vell27w.mall.vo.GoodsListVO;

import java.util.List;

/**
 * 商品业务实现。
 *
 * 【这个类为什么这么薄？】
 * 因为它做的事就是「查库 + 转换」，没有业务规则。
 * 这很正常 —— 越靠近项目后期（下单、库存），Service 才会越厚。
 * 现在这样是对的，不用为了「显得有逻辑」硬塞东西进来。
 *
 * 【它后面会长成什么样】
 *   以后会给 getDetail 加 Redis 缓存（先查缓存、没有回源查库再写缓存）
 *   那时这个类才会开始有真正的分支逻辑。
 *   现在保持干净，是为了到时候改起来看得清楚。
 */
@Service
public class GoodsServiceImpl implements GoodsService {

    private final GoodsInfoMapper goodsInfoMapper;

    public GoodsServiceImpl(GoodsInfoMapper goodsInfoMapper) {
        this.goodsInfoMapper = goodsInfoMapper;
    }

    /**
     * 分页查询商品列表。
     *
     * 【为什么先 count 再 findPage，而不是反过来？】
     * 顺序其实无所谓，两次查询本来就无法做到原子。
     * 两次查询之间存在时间差，期间若有商品上下架，
     * total 和 list 就可能对不上（total 说有 3 页，翻到第 3 页是空的）。
     *
     * 这不是 bug，是「分页的固有特性」—— 除非用快照读或缓存，
     * 任何分页实现都有这个问题。面试被问到说清楚就够了，
     * 不用（也没法）在这个层面修掉它。
     *
     * ⚠️ 注意 service 层这里【没做任何筛选条件的加工】：
     * 「只查上架商品」这个条件写死在 Mapper 的 XML 里，
     * 排序白名单也写死在 XML 里。这样 Service 无法绕过它们 ——
     * 公开接口的这两条安全底线，不该由调用方自觉。
     */
    @Override
    public PageResult<GoodsListVO> page(GoodsQueryParam query) {
        long total = goodsInfoMapper.countByCondition(query);
        List<GoodsListVO> list = goodsInfoMapper.findPage(query);
        return new PageResult<>(total, query.getPageNum(), query.getPageSize(), list);
    }

    /**
     * 查商品详情。
     *
     * 【为什么查不到时抛异常，而不是返回 null？】
     * 返回 null 的话，
     * Controller 得自己判断，每个调用点都要写一遍 if；漏写一处，
     * null 就会变成 {"code":200,"data":null} 传出去，调用方以为成功了。
     * 抛异常则由 GlobalExceptionHandler 统一转成 404 结构 ——
     * 「查不到要报错」这条规则在代码里只存在一处。
     *
     * 【「不存在」和「已下架」为什么用同一个错误码？】
     * 因为这个接口是公开的：已下架商品不该能被点进来，
     * 返回「该商品已下架」等于变相承认它存在过。
     * 两种情况都按 404 处理，对外看不出区别。
     * （后台管理端的接口就完全不同了，那种地方必须能区分。）
     *
     * 【改坏它会怎样？】
     * 如果把「只查上架」从 SQL 挪到 Java 里判断（先查出来再 if status != 0 抛异常），
     * 结果看着一模一样，但有两个隐患：
     *   ① 已下架商品的数据先被装载进了内存，多一步没必要的开销
     *   ② 以后加缓存时，很容易顺手把已下架商品也缓存进去
     * 能在数据库层面挡掉的，就别放到应用层挡。
     */
    @Override
    public GoodsDetailVO getDetail(Long id) {
        GoodsDetailVO detail = goodsInfoMapper.findOnSaleById(id);
        if (detail == null) {
            // 注意日志和异常信息里【不要区分】「不存在」和「已下架」，
            // 对外要一致。id 可以留着，方便排查是不是前端传错了。
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架: id=" + id);
        }
        return detail;
    }
}
