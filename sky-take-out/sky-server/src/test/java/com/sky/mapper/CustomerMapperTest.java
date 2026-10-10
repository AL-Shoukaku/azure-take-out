package com.sky.mapper;

import com.github.pagehelper.*;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.support.SqlTestSupport;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerMapperTest extends SqlTestSupport {
    @Test void addressUpdatePersistsProvinceCityAndDistrictNamesAndCodes() {
        AddressBookMapper mapper = mapper(AddressBookMapper.class);
        assertThat(mapper.update(AddressBook.builder().id(1L).userId(USER_ID).detail("新详细地址")
                .provinceCode("440000").provinceName("广东省").cityCode("440100").cityName("广州市")
                .districtCode("440106").districtName("天河区").build())).isEqualTo(1);
        assertThat(mapper.getById(1L)).extracting(AddressBook::getProvinceCode,AddressBook::getProvinceName,
                AddressBook::getCityCode,AddressBook::getCityName,AddressBook::getDistrictCode,AddressBook::getDistrictName)
                .containsExactly("440000","广东省","440100","广州市","440106","天河区");
        assertThat(mapper.getById(1L).getDetail()).isEqualTo("新详细地址");
        assertThat(mapper.getById(1L).getUserId()).isEqualTo(USER_ID);
        assertThat(mapper.getById(1L).getIsDefault()).isEqualTo(1);
    }
    @Test void partialAddressUpdateKeepsOmittedRegionFieldsAndDefaultStatus() {
        mapper(AddressBookMapper.class).insert(AddressBook.builder().userId(USER_ID).consignee("王五")
                .phone("13800000002").provinceCode("440000").provinceName("广东省")
                .cityCode("440100").cityName("广州市").districtCode("440106").districtName("天河区")
                .detail("原详细地址").isDefault(0).build());
        AddressBookMapper mapper = mapper(AddressBookMapper.class);
        AddressBook original = mapper.list(AddressBook.builder().userId(USER_ID).phone("13800000002").build()).get(0);
        assertThat(mapper.update(AddressBook.builder().id(original.getId()).userId(USER_ID).detail("只修改详细地址").build())).isEqualTo(1);
        AddressBook saved = mapper.getById(original.getId());
        assertThat(saved).extracting(AddressBook::getProvinceCode,AddressBook::getProvinceName,
                AddressBook::getCityCode,AddressBook::getCityName,AddressBook::getDistrictCode,AddressBook::getDistrictName)
                .containsExactly("440000","广东省","440100","广州市","440106","天河区");
        assertThat(saved.getDetail()).isEqualTo("只修改详细地址");
        assertThat(saved.getIsDefault()).isZero();
    }
    @Test void addressSqlRejectsForeignOwnerAndMissingIdentityWithoutChangingRows() {
        AddressBookMapper mapper = mapper(AddressBookMapper.class);
        assertThat(mapper.getByIdAndUserId(1L,USER_ID).getId()).isEqualTo(1L);
        assertThat(mapper.getByIdAndUserId(3L,USER_ID)).isNull();
        assertThat(mapper.list(new AddressBook())).isEmpty();
        assertThat(mapper.update(AddressBook.builder().id(3L).userId(USER_ID).detail("越权修改")
                .provinceCode("440000").provinceName("广东省").cityCode("440100").cityName("广州市")
                .districtCode("440106").districtName("天河区").build())).isZero();
        assertThat(mapper.update(AddressBook.builder().id(1L).detail("缺少身份").build())).isZero();
        assertThat(mapper.deleteById(3L,USER_ID)).isZero();
        assertThat(mapper.deleteById(1L,null)).isZero();
        assertThat(mapper.getById(3L).getDetail()).isEqualTo("他人地址");
        assertThat(mapper.getById(3L)).extracting(AddressBook::getProvinceCode,AddressBook::getProvinceName,
                AddressBook::getCityCode,AddressBook::getCityName,AddressBook::getDistrictCode,AddressBook::getDistrictName)
                .containsOnlyNulls();
        assertThat(mapper.getById(1L).getDetail()).isEqualTo("测试路1号");
    }
    @Test void addressListFiltersUserPhoneAndDefault() {
        AddressBookMapper mapper = mapper(AddressBookMapper.class);
        assertThat(mapper.list(AddressBook.builder().userId(42L).build())).extracting(AddressBook::getId).containsExactly(1L,2L);
        assertThat(mapper.list(AddressBook.builder().userId(42L).phone("13800000000").isDefault(1).build())).extracting(AddressBook::getId).containsExactly(1L);
    }
    @Test void addressDefaultUpdateOnlyClearsSelectedUsersAddresses() {
        AddressBookMapper mapper = mapper(AddressBookMapper.class); mapper.updateIsDefaultByUserId(AddressBook.builder().userId(42L).isDefault(0).build());
        mapper.update(AddressBook.builder().id(2L).userId(USER_ID).isDefault(1).detail("改后地址").build());
        assertThat(mapper.getById(1L).getIsDefault()).isZero(); assertThat(mapper.getById(2L).getIsDefault()).isEqualTo(1);
        assertThat(mapper.getById(2L).getDetail()).isEqualTo("改后地址"); assertThat(mapper.getById(3L).getIsDefault()).isEqualTo(1);
    }
    @Test void addressInsertAndDeleteRoundTrip() {
        AddressBookMapper mapper = mapper(AddressBookMapper.class); mapper.insert(AddressBook.builder().userId(42L).consignee("王五").phone("13800000002").detail("新地址").isDefault(0).build());
        List<AddressBook> list = mapper.list(AddressBook.builder().userId(42L).phone("13800000002").build()); assertThat(list).hasSize(1);
        mapper.deleteById(list.get(0).getId(),USER_ID); assertThat(mapper.getById(list.get(0).getId())).isNull();
    }
    @Test void cartDistinguishesUserProductAndFlavor() {
        ShoppingCartMapper mapper = mapper(ShoppingCartMapper.class);
        assertThat(mapper.list(ShoppingCart.builder().userId(42L).dishId(1L).dishFlavor("微辣").build())).extracting(ShoppingCart::getId).containsExactly(1L);
        assertThat(mapper.list(ShoppingCart.builder().userId(42L).dishId(1L).dishFlavor("中辣").build())).isEmpty();
        assertThat(mapper.list(ShoppingCart.builder().userId(43L).build())).extracting(ShoppingCart::getId).containsExactly(3L);
    }
    @Test void cartInsertBatchUpdateDeleteAndCleanDoNotAffectOtherUsers() {
        ShoppingCartMapper mapper = mapper(ShoppingCartMapper.class);
        ShoppingCart newItem = ShoppingCart.builder().userId(42L).setmealId(1L).name("套餐").number(1).amount(new BigDecimal("29.90")).build();
        mapper.insert(newItem); mapper.batchInsert(Collections.singletonList(ShoppingCart.builder().userId(42L).dishId(3L).number(1).amount(BigDecimal.TEN).build()));
        ShoppingCart saved = mapper.list(ShoppingCart.builder().userId(42L).setmealId(1L).build()).get(0); saved.setNumber(3); mapper.update(saved);
        assertThat(mapper.list(ShoppingCart.builder().userId(42L).setmealId(1L).build()).get(0).getAmount()).isEqualByComparingTo("29.90");
        assertThat(mapper.list(ShoppingCart.builder().userId(42L).setmealId(1L).build()).get(0).getNumber()).isEqualTo(3);
        mapper.deleteById(saved.getId()); mapper.cleanAllByUserId(42L);
        assertThat(mapper.list(ShoppingCart.builder().userId(42L).build())).isEmpty(); assertThat(mapper.list(ShoppingCart.builder().userId(43L).build())).hasSize(1);
    }
    @Test void employeeInsertDefaultStatusAndPartialUpdate() {
        EmployeeMapper mapper = mapper(EmployeeMapper.class);
        mapper.insert(Employee.builder().name("新员工").username("new").password("hash").phone("13800000002").sex("1").idNumber("123456789012345670").build());
        Employee saved = mapper.getByUsername("new"); assertThat(saved.getStatus()).isEqualTo(1);
        mapper.update(Employee.builder().id(saved.getId()).status(0).phone("13800000003").build());
        assertThat(mapper.getByUsername("new").getStatus()).isZero(); assertThat(mapper.getByUsername("new").getPassword()).isEqualTo("hash");
    }
    @Test void employeePageFiltersNameAndOrdersByCreationTime() {
        EmployeePageQueryDTO dto = new EmployeePageQueryDTO(); dto.setName("员工"); PageHelper.startPage(1,10);
        assertThat(mapper(EmployeeMapper.class).pageQuery(dto)).extracting(Employee::getUsername).containsExactly("disabled");
        assertThat(mapper(EmployeeMapper.class).getById(1L).getUsername()).isEqualTo("admin");
    }
    @Test void userSaveBackfillsIdAndDateCountsIncludeBoundaries() {
        UserMapper mapper = mapper(UserMapper.class); User newUser = User.builder().openid("new-openid").name("新用户").createTime(LocalDateTime.of(2026,10,3,0,0)).build();
        mapper.save(newUser); assertThat(newUser.getId()).isNotNull(); assertThat(mapper.getUserByOpenid("new-openid").getId()).isEqualTo(newUser.getId());
        assertThat(mapper.getById(newUser.getId()).getName()).isEqualTo("新用户");
        assertThat(mapper.getTotalUserByTime(LocalDateTime.of(2026,10,3,0,0))).isEqualTo(3);
        assertThat(mapper.getNewUserByTime(LocalDateTime.of(2026,10,1,0,0),LocalDateTime.of(2026,10,2,12,0))).isEqualTo(2);
        Map<String,Object> filter = new HashMap<>(); filter.put("begin",LocalDateTime.of(2026,10,2,0,0)); filter.put("end",LocalDateTime.of(2026,10,3,0,0));
        assertThat(mapper.countByMap(filter)).isEqualTo(2);
    }
    @Test void orderDetailsBatchInsertAndSalesExcludeUncompletedOrders() {
        OrderDetailMapper mapper = mapper(OrderDetailMapper.class);
        mapper.batchInsert(Collections.singletonList(OrderDetail.builder().orderId(2L).setmealId(1L).name("套餐").number(2).amount(new BigDecimal("29.90")).build()));
        assertThat(mapper.getByOrderId(2L)).hasSize(3);
        List<GoodsSalesDTO> sales = mapper.getSalesTop10(LocalDateTime.of(2026,10,1,0,0),LocalDateTime.of(2026,10,4,23,59,59));
        assertThat(sales.get(0).getName()).isEqualTo("鸡丁"); assertThat(sales.get(0).getNumber()).isEqualTo(5);
        assertThat(sales).filteredOn(s -> s.getName().equals("米饭")).extracting(GoodsSalesDTO::getNumber).containsExactly(1);
    }
    @Test void salesRankingIsLimitedToTenRows() {
        List<OrderDetail> details = new ArrayList<>();
        for (int i=1;i<=12;i++) details.add(OrderDetail.builder().orderId(2L).name("商品"+i).number(i+100).amount(BigDecimal.ONE).build());
        OrderDetailMapper mapper = mapper(OrderDetailMapper.class); mapper.batchInsert(details);
        List<GoodsSalesDTO> result = mapper.getSalesTop10(null,null); assertThat(result).hasSize(10);
        assertThat(result.get(0).getNumber()).isEqualTo(112); assertThat(result.get(9).getNumber()).isEqualTo(103);
    }
}
