package io.nuls.nulsswitch.serviceImpl;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.baomidou.mybatisplus.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import io.nuls.nulsswitch.constant.SwitchConstant;
import io.nuls.nulsswitch.entity.Order;
import io.nuls.nulsswitch.entity.Token;
import io.nuls.nulsswitch.mapper.OrderMapper;
import io.nuls.nulsswitch.mapper.TokenMapper;
import io.nuls.nulsswitch.service.OrderService;
import io.nuls.nulsswitch.web.dto.order.QueryOrderReqDto;
import io.nuls.nulsswitch.web.dto.order.QueryOrderResDto;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户交易委托表：包括当前委托、历史委托 服务实现类
 * </p>
 *
 * @author Edward123
 * @since 2019-07-16
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Resource
    TokenMapper tokenMapper;

    @Override
    public Page<Order> queryCanTxOrderByPage(QueryOrderReqDto reqDto) {
        Page<Order> orderPage = new Page<>();
        Order order = new Order();
        BeanUtils.copyProperties(reqDto, order);
        order.setAddress(null);
        orderPage.setCurrent(reqDto.getCurrent() == null ? 1 : reqDto.getCurrent());
        orderPage.setSize(reqDto.getPageSize() == null ? 10 : reqDto.getPageSize());
        EntityWrapper<Order> eWrapper = new EntityWrapper<>(order);
        if (reqDto.getStartQueryTime() != null) {
            eWrapper.ge("create_time", reqDto.getStartQueryTime());
        }
        if (reqDto.getEndQueryTime() != null) {
            eWrapper.le("create_time", reqDto.getEndQueryTime());
        }
        eWrapper.in("status", Arrays.asList(SwitchConstant.TX_ORDER_STATUS_INIT, SwitchConstant.TX_ORDER_STATUS_PART));
        eWrapper.notIn("address", reqDto.getAddress());
        eWrapper.orderBy("create_time", false);
        return this.selectPage(orderPage, eWrapper);
    }

    @Override
    public Page<QueryOrderResDto> queryOrderByPage(QueryOrderReqDto reqDto) {
        Page<QueryOrderResDto> orderDtoPage = new Page<>();
        Page<Order> orderPage = new Page<>();
        Order order = new Order();
        BeanUtils.copyProperties(reqDto, order);
        orderPage.setCurrent(reqDto.getCurrent() == null ? 1 : reqDto.getCurrent());
        orderPage.setSize(reqDto.getPageSize() == null ? 10 : reqDto.getPageSize());
        EntityWrapper<Order> eWrapper = new EntityWrapper<>(order);
        if (reqDto.getStartQueryTime() != null) {
            eWrapper.ge("create_time", reqDto.getStartQueryTime());
        }
        if (reqDto.getEndQueryTime() != null) {
            eWrapper.le("create_time", reqDto.getEndQueryTime());
        }
        if (reqDto.getCanTx()!=null && reqDto.getCanTx()) {
            eWrapper.in("status", Arrays.asList(SwitchConstant.TX_ORDER_STATUS_INIT, SwitchConstant.TX_ORDER_STATUS_PART));
        }
        eWrapper.orderBy("create_time", false);
        orderPage = this.selectPage(orderPage, eWrapper);
        BeanUtils.copyProperties(orderPage, orderDtoPage);
        Map<Integer, String> tokenMap = getTokenMap();
        if (orderPage != null && orderPage.getRecords() != null) {
            List<QueryOrderResDto> list = Lists.newArrayList();
            orderPage.getRecords().forEach(obj -> {
                QueryOrderResDto orderResDto = new QueryOrderResDto();
                BeanUtils.copyProperties(obj, orderResDto);
                orderResDto.setTokenPair(tokenMap.get(obj.getFromTokenId()) + "_" + tokenMap.get(obj.getToTokenId()));
                list.add(orderResDto);
            });
            orderDtoPage.setRecords(list);
        }
        return orderDtoPage;
    }

    public Map<Integer, String> getTokenMap() {
        // 查询所有token
        Map<Integer, String> tokenMap = Optional.ofNullable(tokenMapper.selectList(null)).orElse(Collections.emptyList()).stream().collect(Collectors.toMap(Token::getTokenId, Token::getTokenSymbol));
        return tokenMap;
    }
}
