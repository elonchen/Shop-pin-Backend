package cn.edu.neu.shop.pin.service;

import cn.edu.neu.shop.pin.exception.OrderItemsAreNotInTheSameStoreException;
import cn.edu.neu.shop.pin.exception.ProductSoldOutException;
import cn.edu.neu.shop.pin.exception.RecordNotFoundException;
import cn.edu.neu.shop.pin.mapper.PinOrderIndividualMapper;
import cn.edu.neu.shop.pin.model.PinOrderIndividual;
import cn.edu.neu.shop.pin.model.PinOrderItem;
import cn.edu.neu.shop.pin.model.PinUser;
import cn.edu.neu.shop.pin.model.PinUserAddress;
import cn.edu.neu.shop.pin.util.base.AbstractService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Service
public class OrderIndividualService extends AbstractService<PinOrderIndividual> {

    @Autowired
    private UserRoleListTransferService userRoleListTransferService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private AddressService addressService;

    @Autowired
    private PinOrderIndividualMapper pinOrderIndividualMapper;

    /**
     * 获取最近三个月的OrderIndividual信息
     *
     * @param userId
     * @return
     */
    public List<PinOrderIndividual> getRecentThreeMonthsOrderIndividuals(Integer userId) {
        List<PinOrderIndividual> orderIndividuals =
                pinOrderIndividualMapper.getRecentThreeMonthsOrderIndividuals(userId);
        for (PinOrderIndividual o : orderIndividuals) {
            o.setOrderItems(orderItemService.getOrderItemsByOrderIndividualId(o.getId()));
            o.setStore(storeService.getStoreById(o.getStoreId()));
        }
        return orderIndividuals;
    }

    /**
     * 传入一串PinOrderIndividual，返回它们对应的用户list
     *
     * @param list 一串PinOrderIndividual
     * @return 返回它们对应的用户list
     */
    public List<PinUser> getUsers(List<PinOrderIndividual> list) {
        List<PinUser> users = new ArrayList<>();
        for (PinOrderIndividual item : list) {
            users.add(userRoleListTransferService.findById(item.getUserId()));
        }
        return users;
    }

    /**
     * 提交订单，即把一条OrderItem记录变为Submitted
     *
     * @param user
     * @param list
     * @param addressId
     * @return
     * @throws OrderItemsAreNotInTheSameStoreException
     * @throws ProductSoldOutException
     */
    public PinOrderIndividual addOrderIndividual(PinUser user, List<PinOrderItem> list, Integer addressId, String userRemark) throws OrderItemsAreNotInTheSameStoreException, ProductSoldOutException {
        PinUserAddress address = addressService.findById(addressId);
        if (address == null) {
            throw new RecordNotFoundException("地址ID不正确");
        }
        boolean isSameStore = productService.isBelongSameStore(list);
        //如果属于一家店铺
        if (isSameStore) {
            Integer amount = orderItemService.getProductAmount(list);
            if (amount == -1) {
                //库存不够，只能终止这次创建orderIndividual
                throw new ProductSoldOutException("库存不足");
            }
            int storeId = productService.getProductById(list.get(0).getProductId()).getStoreId();    // 店铺id
            BigDecimal originallyPrice = orderItemService.getProductTotalPrice(list);   // 计算本来的价格
            BigDecimal shippingFee = orderItemService.getAllShippingFee(list);  // 邮费
            BigDecimal totalPrice = originallyPrice.add(shippingFee);   //总费用
            // OrderItemService.PayDetail payDetail = orderItemService.new PayDetail(user.getId(), totalPrice);    //支付详情
            BigDecimal totalCost = orderItemService.getTotalCost(list);
            String addressString = address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail();
            PinOrderIndividual orderIndividual = new PinOrderIndividual(null, storeId, user.getId(),
                    address.getRealName(), address.getPhone(), addressString,
                    orderItemService.getProductAmount(list), totalPrice/*总价格 邮费加本来的费用*/,
                    shippingFee, null, /*卖家可以改动实际支付的邮费，修改的时候总价格也要修改，余额支付，实际支付也要改*/
                    null, null, false, null,
                    new Date(System.currentTimeMillis()), 0, 0, null, null, null,
                    null, null, null, null, null, null, null, userRemark, null, totalCost);
            this.save(orderIndividual);
            //将list中的PinOrderItem挂载到PinOrderIndividual上
            orderItemService.mountOrderItems(list, orderIndividual.getId());
            return orderIndividual;
        } else {
            //如果不属于一家店铺
            throw new OrderItemsAreNotInTheSameStoreException("不属于一家店铺");
        }
    }

    /**
     * @author flyhero
     * 根据OrderGroupId获取在此团单内的OrderIndividual的List
     * @param orderGroupId
     * @return
     */
    public List<PinOrderIndividual> getOrderIndividualsByOrderGroupId(Integer orderGroupId) {
        PinOrderIndividual orderIndividual = new PinOrderIndividual();
        orderIndividual.setOrderGroupId(orderGroupId);
        List<PinOrderIndividual> orderIndividuals = pinOrderIndividualMapper.select(orderIndividual);
        return orderIndividuals;
    }

    /**
     * 获取订单数
     * @param storeId
     * @return
     */
    public Integer[] getOrders(Integer storeId) {
        Integer order[] = new Integer[7];
        Date date = new Date();
        date = getTomorrow(date);
        for(int i = 0; i < 7; i++) {
            Date toDate = date;
            date = getYesterday(date);
            java.util.Date fromDate = date;
//            System.out.println("fromDate: " + fromDate + " --- toDate: " + toDate);
            order[i] = pinOrderIndividualMapper.getNumberOfOrder(fromDate, toDate, storeId);
//            System.out.println("comment[i]: " + comment[i]);
        }
        return order;
    }

    private Date getYesterday(Date today) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) - 1);
        return calendar.getTime();
    }

    private Date getTomorrow(Date today) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 1);
        return calendar.getTime();
    }
}
