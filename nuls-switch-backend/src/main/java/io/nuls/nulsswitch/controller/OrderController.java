package io.nuls.nulsswitch.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.data.Transaction;
import io.nuls.base.signture.P2PHKSignature;
import io.nuls.base.signture.TransactionSignature;
import io.nuls.core.crypto.HexUtil;
import io.nuls.nulsswitch.constant.CommonErrorCode;
import io.nuls.nulsswitch.constant.SwitchConstant;
import io.nuls.nulsswitch.entity.Order;
import io.nuls.nulsswitch.entity.Trade;
import io.nuls.nulsswitch.service.OrderService;
import io.nuls.nulsswitch.service.TradeService;
import io.nuls.nulsswitch.util.IdUtils;
import io.nuls.nulsswitch.util.NulsUtils;
import io.nuls.nulsswitch.util.Preconditions;
import io.nuls.nulsswitch.web.dto.auth.ConfirmTradeReqDto;
import io.nuls.nulsswitch.web.dto.auth.TradeResultReqDto;
import io.nuls.nulsswitch.web.dto.order.QueryOrderReqDto;
import io.nuls.nulsswitch.web.dto.order.QueryOrderResDto;
import io.nuls.nulsswitch.web.dto.order.QueryTradeReqDto;
import io.nuls.nulsswitch.web.exception.NulsRuntimeException;
import io.nuls.nulsswitch.web.vo.trade.TradeVO;
import io.nuls.nulsswitch.web.wrapper.WrapMapper;
import io.nuls.nulsswitch.web.wrapper.Wrapper;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nuls.nulsswitch.constant.SwitchConstant.*;

@RestController
@RequestMapping("/v1/order")
@Slf4j
public class OrderController extends BaseController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private TradeService tradeService;

    @ApiOperation(value = "获取卖出挂单", notes = "分页获取卖出挂单")
    @GetMapping("listOnSell")
    public Wrapper<Page<Order>> listOnSell(QueryOrderReqDto orderReq) {
        // 查询当前卖出单，等待购买列表，排除自己发布的出售委托
        Page<Order> orderPage;
        try {
            // check parameters
            Preconditions.checkNotNull(orderReq.getAddress(), CommonErrorCode.PARAMETER_NULL);
            orderReq.setTxType(SwitchConstant.TX_TYPE_SELL);
            orderPage = orderService.queryCanTxOrderByPage(orderReq);
            log.info("listOnSell response:{}", JSON.toJSONString(WrapMapper.ok(orderPage)));
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(orderPage);
    }

    @ApiOperation(value = "获取买入挂单", notes = "分页获取买入挂单")
    @GetMapping("listOnBuy")
    public Wrapper<Page<Order>> listOnBuy(QueryOrderReqDto orderReq) {
        // 查询当前卖入单，等待卖出列表，排除自己发布的购买委托
        Page<Order> orderPage;
        try {
            // check parameters
            Preconditions.checkNotNull(orderReq.getAddress(), CommonErrorCode.PARAMETER_NULL);
            orderReq.setTxType(SwitchConstant.TX_TYPE_BUY);
            orderPage = orderService.queryCanTxOrderByPage(orderReq);
            log.info("listOnBuy response:{}", JSON.toJSONString(WrapMapper.ok(orderPage)));
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(orderPage);
    }

    @ApiOperation(value = "用户挂单", notes = "用户挂单")
    @PostMapping("createOrder")
    public Wrapper<String> createOrder(@RequestBody Order order) {
        try {
            // check parameters
            Preconditions.checkNotNull(order, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(order.getTxType(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(order.getAddress(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(order.getFromTokenId(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(order.getToTokenId(), CommonErrorCode.PARAMETER_NULL);

            // check price,totalNum
            Preconditions.checkArgument(order.getPrice() != null && order.getPrice().doubleValue() > 0, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkArgument(order.getTotalNum() != null && order.getTotalNum() > 0, CommonErrorCode.PARAMETER_NULL);

            // save order
            // 订单ID生成
            order.setOrderId(IdUtils.getIncreaseIdByNanoTime());
            order.setStatus(SwitchConstant.TX_ORDER_STATUS_INIT);
            order.setTxNum(0L);
            orderService.insert(order);
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(order.getOrderId());
    }

    @ApiOperation(value = "用户撤单", notes = "用户撤单")
    @PostMapping("cancelOrder")
    public Wrapper<Boolean> cancelOrder(@RequestBody Order order) {
        Boolean result;
        try {
            // check parameters
            Preconditions.checkNotNull(order, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(order.getOrderId(), CommonErrorCode.PARAMETER_NULL);

            // cancel order
            result = orderService.cancelOrderTrade(order.getOrderId());
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(result);
    }

    @ApiOperation(value = "用户吃单", notes = "用户吃单")
    @PostMapping("tradingOrder")
    public Wrapper<Boolean> tradingOrder(@RequestBody Trade trade) {
        Boolean result;
        try {
            Long txNum = trade.getTxNum();
            // check parameters
            Preconditions.checkNotNull(trade, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(trade.getAddress(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(trade.getOrderId(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(trade.getTxNum(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(trade.getTxHex(), CommonErrorCode.PARAMETER_NULL);

            // check data
            Order order = orderService.selectById(trade.getOrderId());
            if (order == null) {
                log.error("the order does not exist,orderId:{}", trade.getOrderId());
                throw new NulsRuntimeException(CommonErrorCode.DATA_NOT_FOUND);
            }

            long remainNum = order.getTotalNum() - order.getTxNum();
            Preconditions.checkArgument(txNum > 0 && txNum <= remainNum, CommonErrorCode.PARAMETER_ERROR);

            // create order trade
            // 交易ID生成
            trade.setTxId(IdUtils.getIncreaseIdByNanoTime());
            trade.setStatus(SwitchConstant.TX_TRADE_STATUS_WAIT);
            // 根据价格和源代币交易量，计算目标代币数量，在前端计算
            tradeService.insert(trade);

            // 更新订单状态 update order status
            order.setStatus(SwitchConstant.TX_ORDER_STATUS_PART);
            result = orderService.updateById(order);
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(result);
    }

    @ApiOperation(value = "撤销交易", notes = "撤销交易")
    @PostMapping("cancelTrade")
    public Wrapper<Boolean> cancelTrade(@RequestBody Trade trade) {
        try {
            // check parameters
            Preconditions.checkNotNull(trade, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(trade.getTxId(), CommonErrorCode.PARAMETER_NULL);

            // cancel trade
            tradeService.cancelOrderTrade(trade.getOrderId(), trade.getTxId());
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(true);
    }

    @ApiOperation(value = "查询用户当前委托订单", notes = "查询用户当前委托订单")
    @GetMapping("queryMyCurrentOrder")
    public Wrapper<Page<QueryOrderResDto>> queryMyCurrentOrder(QueryOrderReqDto orderReq) {
        // 查询用户当前委托订单，包含未交易、部分交易的订单
        Page<QueryOrderResDto> orderPage;
        try {
            // check parameters
            Preconditions.checkNotNull(orderReq, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(orderReq.getAddress(), CommonErrorCode.PARAMETER_NULL);

            // 只查询可交易的订单
            orderReq.setCanTx(true);
            orderPage = orderService.queryOrderByPage(orderReq);
            log.info("queryMyCurrentOrder response:{}", JSON.toJSONString(WrapMapper.ok(orderPage)));
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(orderPage);
    }

    @ApiOperation(value = "查询用户历史委托订单", notes = "查询用户历史委托订单")
    @GetMapping("queryMyHisOrder")
    public Wrapper<Page<QueryOrderResDto>> queryMyHisOrder(QueryOrderReqDto orderReq) {
        // 查询用户历史委托订单，包含所有交易状态的订单
        Page<QueryOrderResDto> orderPage;
        try {
            // check parameters
            Preconditions.checkNotNull(orderReq, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(orderReq.getAddress(), CommonErrorCode.PARAMETER_NULL);
            orderPage = orderService.queryOrderByPage(orderReq);
            log.info("queryMyHisOrder response:{}", JSON.toJSONString(WrapMapper.ok(orderPage)));
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(orderPage);
    }

    @ApiOperation(value = "查询用户历史交易", notes = "查询用户历史交易")
    @GetMapping("queryTradeByPage")
    public Wrapper<Page<TradeVO>> queryTradeByPage(QueryTradeReqDto tradeReq) {
        // 查询用户历史交易
        Page<TradeVO> tradePage;
        try {
            // check parameters
            Preconditions.checkNotNull(tradeReq, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(tradeReq.getAddress(), CommonErrorCode.PARAMETER_NULL);
            tradePage = tradeService.queryTradeByPage(tradeReq);
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(tradePage);
    }

    @ApiOperation(value = "查询订单明细", notes = "查询订单明细")
    @GetMapping("getOrderDetail")
    public Wrapper<Page<Trade>> getOrderDetail(QueryTradeReqDto tradeReq) {
        Page<Trade> tradePage = new Page<>();
        try {
            // check parameters
            Preconditions.checkNotNull(tradeReq, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(tradeReq.getOrderId(), CommonErrorCode.PARAMETER_NULL);

            // query order trade detail
            Trade trade = new Trade();
            trade.setOrderId(tradeReq.getOrderId());
            EntityWrapper<Trade> eWrapper = new EntityWrapper<>(trade);
            tradePage.setCurrent(tradeReq.getCurrent() == null ? 1 : tradeReq.getCurrent());
            tradePage.setSize(tradeReq.getPageSize() == null ? 10 : tradeReq.getPageSize());
            eWrapper.orderBy("create_time", false);
            tradeService.selectPage(tradePage, eWrapper);
            log.info("getOrderDetail response:{}", JSON.toJSONString(WrapMapper.ok(tradePage)));
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(tradePage);
    }

    /**
     * 交易发起方确认交易
     * 1、将16进制串反序列化为交易
     * 2、根据tradeId与交易对象对比请求合法性(交易对象的地址是否与交易记录中的相匹配)
     * 3、更新数据表记录状态为等待区块链确认中
     * 4、响应确认提交成功
     */
    @ApiOperation(value = "确认订单", notes = "确认订单")
    @PostMapping("confirmOrder")
    public Wrapper confirmOrder(@RequestBody ConfirmTradeReqDto confirmTradeReqDto) {
        String hash = null;
        Boolean result;
        try {
            // check parameters
            Preconditions.checkNotNull(confirmTradeReqDto.getTxId(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(confirmTradeReqDto.getDataHex(), CommonErrorCode.PARAMETER_NULL);

            // check data
            Trade trade = tradeService.selectById(confirmTradeReqDto.getTxId());
            if (trade == null) {
                log.warn("the trade does not exist,txId:{}", confirmTradeReqDto.getTxId());
                throw new NulsRuntimeException(CommonErrorCode.PARAMETER_ERROR);
            }
            // 最后确认产生的交易
            Transaction transaction = Transaction.getInstance(HexUtil.decode(confirmTradeReqDto.getDataHex()));
            TransactionSignature transactionSignature = new TransactionSignature();
            transactionSignature.parse(new NulsByteBuffer(transaction.getTransactionSignature()));
            // 签名地址列表
            List<String> addressList = new ArrayList<>(2);
            // 检查签名地址列表
            for (P2PHKSignature p2PHKSignature : transactionSignature.getP2PHKSignatures()) {
                String address = AddressTool.getStringAddressByBytes(AddressTool.getAddress(p2PHKSignature.getPublicKey(), 2));
                addressList.add(address);
            }
            // 检查签名地址是否包括下单人地址
            if (!addressList.contains(trade.getAddress())) {
                log.warn("the txData trade address is incorrect,txId:{}", confirmTradeReqDto.getTxId());
                throw new NulsRuntimeException(CommonErrorCode.PARAMETER_ERROR);
            }
            // 检查签名地址是否包括挂单人地址
            Order order = orderService.selectById(trade.getOrderId());
            if (!addressList.contains(order.getAddress())) {
                log.warn("the txData order address is incorrect,txId:{}", confirmTradeReqDto.getTxId());
                throw new NulsRuntimeException(CommonErrorCode.PARAMETER_ERROR);
            }

            // 更新交易状态为交易确认中，等待定时任务同步区块链交易状态
            trade.setStatus(SwitchConstant.TX_TRADE_STATUS_CONFIRMING);
            result = tradeService.updateById(trade);
            log.info("confirmOrder response:{}", result);
        } catch (NulsRuntimeException ex) {
            log.error("", ex);
            return WrapMapper.error(ex.getErrorCode());
        } catch (Exception e) {
            log.error("", e);
            if (hash == null) {
                return WrapMapper.error(CommonErrorCode.BROADCAST_ERROR);
            }
            return WrapMapper.error("System Error");
        }
        return WrapMapper.ok(result);
    }

    /**
     * 更新交易数据上链返回结果
     * 将交易状态改为交易失败
     */
    @ApiOperation(value = "更新交易数据上链返回结果", notes = "验证交易数据错误时才调用该接口")
    @PostMapping("updateTradeResult")
    public Wrapper updateTradeResult(@RequestBody TradeResultReqDto tradeResultReqDto) {
        Boolean result;
        try {
            // check parameters
            Preconditions.checkNotNull(tradeResultReqDto.getTxId(), CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(tradeResultReqDto.getDataHex(), CommonErrorCode.PARAMETER_NULL);

            // check data
            Trade trade = tradeService.selectById(tradeResultReqDto.getTxId());
            if (trade == null) {
                log.warn("the trade does not exist,txId:{}", tradeResultReqDto.getTxId());
                throw new NulsRuntimeException(CommonErrorCode.PARAMETER_ERROR);
            }

            // txHex中增加了第二次追加的签名
            trade.setTxHex(tradeResultReqDto.getDataHex());
            // 交易状态为交易失败
            trade.setStatus(SwitchConstant.TX_TRADE_STATUS_FAIL);
            // 返回错误信息
            trade.setMsg(tradeResultReqDto.getMsg());
            result = tradeService.updateById(trade);
            log.info("updateTradeResult response:{}", result);
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(result);
    }

    /**
     * 查询订单本地最新交易，只查询有效的交易，不包含交易失败、撤销的交易
     *
     * @param tradeReq
     * @return
     */
    @ApiOperation(value = "查询订单本地最新nonce", notes = "查询订单本地最新nonce")
    @GetMapping("getLastOrderNonce")
    public Wrapper<String> getLastOrderNonce(QueryTradeReqDto tradeReq) {
        String txHash;
        try {
            // check parameters
            Preconditions.checkNotNull(tradeReq, CommonErrorCode.PARAMETER_NULL);
            Preconditions.checkNotNull(tradeReq.getOrderId(), CommonErrorCode.PARAMETER_NULL);

            // query order trade detail
            Trade trade = new Trade();
            trade.setOrderId(tradeReq.getOrderId());
            EntityWrapper<Trade> eWrapper = new EntityWrapper<>(trade);
            eWrapper.orderBy("create_time", false);
            eWrapper.in("status", Arrays.asList(TX_TRADE_STATUS_WAIT, TX_TRADE_STATUS_CONFIRMING, TX_TRADE_STATUS_CONFIRMED));
            eWrapper.eq("address", tradeReq.getAddress());
            List<Trade> list = tradeService.selectList(eWrapper);
            if (list != null && list.size() > 0) {
                Trade lastTrade = list.get(0);
                txHash = NulsUtils.getNonceEncodeByTxHash(lastTrade.getTxHash());
            } else {
                txHash = "";
            }
            log.info("getLastOrderNonce txHash:{}", txHash);
        } catch (NulsRuntimeException ex) {
            return WrapMapper.error(ex.getErrorCode());
        }
        return WrapMapper.ok(txHash);
    }

}
