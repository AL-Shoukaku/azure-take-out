package com.azure.service.impl;

import com.azure.dto.DishDTO;
import com.azure.dto.SetmealDTO;
import com.azure.entity.DishFlavor;
import com.azure.entity.SetmealDish;
import com.azure.entity.ShoppingCart;
import com.azure.mapper.*;
import com.azure.support.TransactionTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise production services, transaction proxies and SQL against an isolated H2 database. */
class ShoppingCartSyncIntegrationTest extends TransactionTestSupport {
    private ShoppingCartMapper carts;
    private DishServiceImpl dishTarget;
    private SetmealServiceImpl setmealTarget;
    private DishServiceImpl dishes;
    private SetmealServiceImpl setmeals;
    private JdbcTemplate jdbc;

    @BeforeEach
    void initializeServices() {
        jdbc = new JdbcTemplate(dataSource);
        carts = transactionalMapper(ShoppingCartMapper.class);

        dishTarget = new DishServiceImpl();
        inject(dishTarget, "dishMapper", transactionalMapper(DishMapper.class));
        inject(dishTarget, "dishFlavorMapper", transactionalMapper(DishFlavorMapper.class));
        inject(dishTarget, "setMealDishMapper", transactionalMapper(SetmealDishMapper.class));
        inject(dishTarget, "shoppingCartMapper", carts);
        inject(dishTarget, "cacheClient", mockedCacheClient());
        dishes = transactionalService(dishTarget, DishServiceImpl.class);

        setmealTarget = new SetmealServiceImpl();
        inject(setmealTarget, "setmealMapper", transactionalMapper(SetmealMapper.class));
        inject(setmealTarget, "setmealDishMapper", transactionalMapper(SetmealDishMapper.class));
        inject(setmealTarget, "shoppingCartMapper", carts);
        inject(setmealTarget, "cacheClient", mockedCacheClient());
        setmeals = transactionalService(setmealTarget, SetmealServiceImpl.class);
    }

    private void addCart(boolean dish, long productId, long userId, String flavor, int number, String price) {
        carts.insert(ShoppingCart.builder().userId(userId)
                .dishId(dish ? productId : null).setmealId(dish ? null : productId)
                .name("旧名称").image("old.png").dishFlavor(flavor).number(number)
                .amount(new BigDecimal(price)).createTime(LocalDateTime.of(2026, 10, 8, 10, 0)).build());
    }

    private List<ShoppingCart> productCarts(boolean dish, long productId) {
        return carts.list(new ShoppingCart()).stream()
                .filter(row -> Objects.equals(dish ? row.getDishId() : row.getSetmealId(), productId))
                .sorted(Comparator.comparing(ShoppingCart::getId)).toList();
    }

    private DishDTO dishUpdate() {
        DishDTO dto = new DishDTO();
        dto.setId(1L);
        dto.setName("新版鸡丁");
        dto.setPrice(new BigDecimal("30.00"));
        dto.setImage("new-dish.png");
        dto.setStatus(1);
        dto.setFlavors(List.of(DishFlavor.builder().name("辣度").value("[\"微辣\",\"中辣\"]").build()));
        return dto;
    }

    private SetmealDTO setmealUpdate() {
        SetmealDTO dto = new SetmealDTO();
        dto.setId(1L);
        dto.setName("新版套餐");
        dto.setPrice(new BigDecimal("60.00"));
        dto.setImage("new-setmeal.png");
        dto.setStatus(1);
        dto.setSetmealDishes(List.of(SetmealDish.builder().dishId(2L).name("米饭")
                .price(new BigDecimal("8.00")).copies(2).build()));
        return dto;
    }

    @Test
    void dishEditSynchronizesAllUsersAndFlavorsWithoutChangingSelectionsOrHistory() {
        addCart(true, 1L, USER_ID, "中辣", 3, "20.00");
        addCart(true, 1L, 43L, null, 4, "25.00");
        addCart(false, 1L, USER_ID, null, 2, "29.90");
        List<ShoppingCart> before = productCarts(true, 1L);
        List<ShoppingCart> unrelated = carts.list(new ShoppingCart()).stream()
                .filter(row -> !Objects.equals(row.getDishId(), 1L)).toList();
        var history = jdbc.queryForList("select * from order_detail order by id");

        dishes.updateWithFlavor(dishUpdate());

        List<ShoppingCart> after = productCarts(true, 1L);
        assertThat(after).hasSize(4).allSatisfy(row -> {
            assertThat(row.getAmount()).isEqualByComparingTo("30.00");
            assertThat(row.getName()).isEqualTo("新版鸡丁");
            assertThat(row.getImage()).isEqualTo("new-dish.png");
        });
        assertThat(after).usingRecursiveComparison().ignoringFields("name", "amount", "image").isEqualTo(before);
        assertThat(carts.list(new ShoppingCart())).containsAll(unrelated);
        assertThat(jdbc.queryForList("select * from order_detail order by id")).isEqualTo(history);
    }

    @Test
    void setmealEditSynchronizesAllUsersWithoutRemovingRowsOrChangingDishWithSameId() {
        addCart(false, 1L, USER_ID, null, 2, "29.90");
        addCart(false, 1L, 43L, null, 3, "39.90");
        addCart(false, 2L, USER_ID, null, 1, "39.90");
        List<ShoppingCart> before = productCarts(false, 1L);
        List<ShoppingCart> unrelated = carts.list(new ShoppingCart()).stream()
                .filter(row -> !Objects.equals(row.getSetmealId(), 1L)).toList();
        var history = jdbc.queryForList("select * from order_detail order by id");

        setmeals.updateWithDishes(setmealUpdate());

        List<ShoppingCart> after = productCarts(false, 1L);
        assertThat(after).hasSize(2).allSatisfy(row -> {
            assertThat(row.getAmount()).isEqualByComparingTo("60.00");
            assertThat(row.getName()).isEqualTo("新版套餐");
            assertThat(row.getImage()).isEqualTo("new-setmeal.png");
        });
        assertThat(after).usingRecursiveComparison().ignoringFields("name", "amount", "image").isEqualTo(before);
        assertThat(carts.list(new ShoppingCart())).containsAll(unrelated);
        assertThat(jdbc.queryForList("select * from order_detail order by id")).isEqualTo(history);
    }

    @ParameterizedTest(name = "stopping product removes cart rows, dish={0}")
    @ValueSource(booleans = {true, false})
    void stoppingProductRemovesItsCartRowsForAllUsersAndPreservesOtherProducts(boolean dish) {
        addCart(false, 1L, USER_ID, null, 2, "29.90");
        addCart(false, 1L, 43L, null, 3, "29.90");
        List<ShoppingCart> unrelated = productCarts(!dish, 1L);
        List<ShoppingCart> rice = productCarts(true, 2L);

        if (dish) dishes.startOrStop(0, 1L);
        else setmeals.startOrStop(0, 1L);

        assertThat(productCarts(dish, 1L)).isEmpty();
        assertThat(productCarts(!dish, 1L)).isEqualTo(unrelated);
        assertThat(productCarts(true, 2L)).isEqualTo(rice);
        assertThat(jdbc.queryForObject("select status from " + (dish ? "dish" : "setmeal") + " where id=1",
                Integer.class)).isZero();
    }

    @ParameterizedTest(name = "deleting product cleans cart without null dereference, dish={0}")
    @ValueSource(booleans = {true, false})
    void deletingStoppedProductCleansCartAfterProductNoLongerExists(boolean dish) {
        long id = dish ? 3L : 2L;
        if (dish) jdbc.update("delete from setmeal_dish where dish_id=3");
        addCart(dish, id, USER_ID, null, 2, "10.00");
        addCart(dish, id, 43L, null, 3, "10.00");
        List<ShoppingCart> unrelated = productCarts(true, 1L);

        if (dish) dishes.batchDelete(List.of(id));
        else setmeals.deleteWithDishes(List.of(id));

        assertThat(productCarts(dish, id)).isEmpty();
        assertThat(productCarts(true, 1L)).isEqualTo(unrelated);
        assertThat(jdbc.queryForObject("select count(*) from " + (dish ? "dish" : "setmeal") + " where id=?",
                Integer.class, id)).isZero();
    }

    @ParameterizedTest(name = "cart synchronization rolls back with catalog edit, dish={0}")
    @ValueSource(booleans = {true, false})
    void synchronizationFailureRollsBackProductRelationsAndAlreadyUpdatedCartRows(boolean dish) {
        addCart(false, 1L, USER_ID, null, 2, "29.90");
        var originalProducts = jdbc.queryForList("select * from " + (dish ? "dish" : "setmeal") + " order by id");
        var originalRelations = jdbc.queryForList("select * from " + (dish ? "dish_flavor" : "setmeal_dish") + " order by id");
        var originalCart = jdbc.queryForList("select * from shopping_cart order by id");
        ShoppingCartMapper failing = spy(carts);
        if (dish) {
            doAnswer(call -> {
                carts.updateByDish(call.getArgument(0));
                throw new IllegalStateException("cart sync failed");
            }).when(failing).updateByDish(any());
            inject(dishTarget, "shoppingCartMapper", failing);
        } else {
            doAnswer(call -> {
                carts.updateBySetmeal(call.getArgument(0));
                throw new IllegalStateException("cart sync failed");
            }).when(failing).updateBySetmeal(any());
            inject(setmealTarget, "shoppingCartMapper", failing);
        }

        assertThatThrownBy(() -> {
            if (dish) dishes.updateWithFlavor(dishUpdate());
            else setmeals.updateWithDishes(setmealUpdate());
        }).isInstanceOf(IllegalStateException.class).hasMessage("cart sync failed");

        assertThat(jdbc.queryForList("select * from " + (dish ? "dish" : "setmeal") + " order by id"))
                .isEqualTo(originalProducts);
        assertThat(jdbc.queryForList("select * from " + (dish ? "dish_flavor" : "setmeal_dish") + " order by id"))
                .isEqualTo(originalRelations);
        assertThat(jdbc.queryForList("select * from shopping_cart order by id")).isEqualTo(originalCart);
    }

    @Test
    void repeatAfterMerchantEditsKeepsExistingAndRepeatedCartRowsAtCurrentPrices() {
        jdbc.update("update order_detail set dish_flavor='微辣' where order_id=1");
        jdbc.update("insert into order_detail (order_id,setmeal_id,name,number,amount) values (1,1,'旧套餐',1,29.90)");
        addCart(false, 1L, USER_ID, null, 2, "29.90");
        var history = jdbc.queryForList("select * from order_detail where order_id=1 order by id");
        dishes.updateWithFlavor(dishUpdate());
        setmeals.updateWithDishes(setmealUpdate());
        OrdersServiceImpl orders = new OrdersServiceImpl();
        inject(orders, "ordersMapper", transactionalMapper(OrdersMapper.class));
        inject(orders, "orderDetailMapper", transactionalMapper(OrderDetailMapper.class));
        inject(orders, "dishMapper", transactionalMapper(DishMapper.class));
        inject(orders, "setmealMapper", transactionalMapper(SetmealMapper.class));
        inject(orders, "shoppingCartMapper", carts);

        orders.repetition(1L);

        List<ShoppingCart> dishRows = productCarts(true, 1L).stream()
                .filter(row -> row.getUserId().equals(USER_ID)).toList();
        assertThat(dishRows).hasSize(2).allSatisfy(row -> {
            assertThat(row.getAmount()).isEqualByComparingTo("30.00");
            assertThat(row.getDishFlavor()).isEqualTo("微辣");
        });
        assertThat(dishRows.stream().mapToInt(ShoppingCart::getNumber).sum()).isEqualTo(4);
        List<ShoppingCart> setmealRows = productCarts(false, 1L);
        assertThat(setmealRows).hasSize(2).allSatisfy(row -> assertThat(row.getAmount()).isEqualByComparingTo("60.00"));
        assertThat(setmealRows.stream().mapToInt(ShoppingCart::getNumber).sum()).isEqualTo(3);
        assertThat(jdbc.queryForList("select * from order_detail where order_id=1 order by id")).isEqualTo(history);
    }
}
