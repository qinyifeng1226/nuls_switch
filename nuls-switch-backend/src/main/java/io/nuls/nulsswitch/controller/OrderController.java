package io.nuls.nulsswitch.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import io.nuls.nulsswitch.dto.GetAllAsksRequestDto;
import io.nuls.nulsswitch.entity.Order;
import io.nuls.nulsswitch.service.OrderService;
import io.nuls.nulsswitch.wrapper.WrapMapper;
import io.nuls.nulsswitch.wrapper.Wrapper;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/v1/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @ApiOperation(value="获取所有的买单", notes = "分页获取所有的买单")
    @GetMapping("getAllAsks")
    public Wrapper<Page<Order>> getAllAsksWithPage(GetAllAsksRequestDto getAllAsksDto){
        Page<Order> orderPage = new Page<>();
        Order order = new Order();
        order.setTxType(1);
        BeanUtils.copyProperties(getAllAsksDto, order);
        orderPage.setCurrent(getAllAsksDto.getCurrent() == null ?  1 : getAllAsksDto.getCurrent());
        orderPage.setSize(getAllAsksDto.getSize() == null ? 10 : getAllAsksDto.getSize());
        EntityWrapper<Order> eWrapper = new EntityWrapper<>(order);
        if (getAllAsksDto.getStartQueryTime() != null) {
            eWrapper.gt("create_time",getAllAsksDto.getStartQueryTime());
        }
        if (getAllAsksDto.getEndQueryTime() != null) {
            eWrapper.lt("create_time",getAllAsksDto.getEndQueryTime());
        }
        orderService.selectPage(orderPage, eWrapper);
        log.info("getAllAsksWithPage response:{}",JSON.toJSONString(WrapMapper.ok(orderPage)));
        return WrapMapper.ok(orderPage);
    }

    @ApiOperation(value="获取所有的卖单", notes = "分页获取所有的卖单")
    @GetMapping("getAllBids")//买家单
    public Wrapper<Long> getAllBidsWithPage(@RequestBody Order param){
        return null;
    }

    @ApiOperation(value="用户挂单", notes = "用户挂单")
    @GetMapping("createOrder")
    public Wrapper<Long> createOrder(@RequestBody Order param){
        return null;
    }

    @ApiOperation(value="用户撤单", notes = "用户撤单")
    @GetMapping("cancelOrder")
    public Wrapper<Long> cancelOrder(@RequestBody Order param){
        return null;
        //return WrapMapper.ok(unitService.create(param));
    }

    @ApiOperation(value="用户吃单", notes = "用户吃单")
    @GetMapping("tradingOrder")
    public Wrapper<Long> tradingOrder(@RequestBody Order param){
        return null;
    }

    @ApiOperation(value="确认订单", notes = "确认订单")
    @GetMapping("confirmOrder")
    public Wrapper<Long> confirmOrder(@RequestBody Order param){
        return null;
    }

    @ApiOperation(value="查询用户委托历史", notes = "查询用户委托历史")
    @GetMapping("getMyOrder")
    public Wrapper<Long> getMyOrder(@RequestBody Order param){
        return null;
    }

    @ApiOperation(value="查询订单明细", notes = "查询订单明细")
    @GetMapping("getMyOrderDetail")
    public Wrapper<Long> getMyOrderDetail(@RequestBody Order param){
        return null;
    }

}
