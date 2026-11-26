package com.popq.platform.cart;

import com.popq.platform.cart.dto.CartRequest;
import com.popq.platform.cart.service.CartService;
import com.popq.platform.order.dto.OrderCreateRequest;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "popq.cart.oracle-tests", matches = "true")
class CartOracleTest {
    @Autowired JdbcTemplate db;
    @Autowired CartService cart;
    @Autowired OrderService orders;
    @Autowired StringRedisTemplate redis;

    @Test
    void verifiesCartAndOrderConcurrency() throws Exception {
        assertEquals("PONG", redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping()));
        db.queryForList("SELECT DELIVERY_ADDRESS_DETAIL FROM ORDERS WHERE 1=0");
        long popup = next("popup_store_seq");
        long product = next("product_seq");
        long[] members = {next("member_seq"), next("member_seq")};
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String[] users = {"cart"+tag+"a@example.test", "cart"+tag+"b@example.test"};
        try {
            db.update("INSERT INTO POPUP_STORE (POP_ID,TITLE,LOCATION,START_DATE,END_DATE,CATEGORY,OP_STATUS) VALUES (?,?,'test',SYSDATE-1,SYSDATE+1,'ENT','OPEN')", popup, "Cart test "+tag);
            db.update("INSERT INTO PRODUCT (PRD_ID,POP_ID,PRODUCT_NAME,PRICE,STOCK,MAX_QTY,SALE_START,SALE_END) VALUES (?,?,?,1000,4,4,SYSTIMESTAMP-INTERVAL '1' DAY,SYSTIMESTAMP+INTERVAL '1' DAY)",product,popup,"Cart test "+tag);
            for (int i=0;i<2;i++) {
                db.update("INSERT INTO MEMBER (MEMBER_ID,EMAIL,PASSWORD,NAME,PHONE,ROLE,STATUS,CREATED_AT) VALUES (?,?,'test-disabled','Cart test',?,'USER','ACTIVE',SYSDATE)",members[i],users[i],"010"+String.format("%08d", members[i] % 100000000));
            }
            parallel(() -> add(users[0],product,2), () -> add(users[0],product,2));
            assertEquals(1, count("SELECT COUNT(*) FROM CART WHERE MEMBER_ID=? AND PRD_ID=?", members[0],product));
            assertEquals(4, count("SELECT QUANTITY FROM CART WHERE MEMBER_ID=? AND PRD_ID=?",members[0],product));
            assertEquals(4, count("SELECT STOCK FROM PRODUCT WHERE PRD_ID=?", product));
            assertThrows(com.popq.platform.cart.exception.CartException.class, () -> add(users[0],product,1));
            assertEquals(4, count("SELECT QUANTITY FROM CART WHERE MEMBER_ID=? AND PRD_ID=?",members[0],product));

            var outcomes = parallel(() -> order(users[0],product), () -> order(users[1],product));
            assertEquals(1, outcomes.stream().filter("CREATED"::equals).count());
            assertEquals(1, outcomes.stream().filter("INSUFFICIENT_STOCK"::equals).count());
            assertEquals(0, count("SELECT STOCK FROM PRODUCT WHERE PRD_ID=?",product));
        } finally {
            for (long member : members) {
                for (Long id : db.queryForList("SELECT ORDER_ID FROM ORDERS WHERE MEMBER_ID=?",Long.class,member)) {
                    redis.delete("order:pending:"+id);
                    db.update("DELETE FROM ORDER_ITEM WHERE ORDER_ID=?",id);
                    db.update("DELETE FROM ORDERS WHERE ORDER_ID=?",id);
                }
                db.update("DELETE FROM CART WHERE MEMBER_ID=?",member);
                db.update("DELETE FROM MEMBER WHERE MEMBER_ID=?",member);
            }
            db.update("DELETE FROM PRODUCT WHERE PRD_ID=?",product);
            db.update("DELETE FROM POPUP_STORE WHERE POP_ID=?",popup);
        }
    }

    private long next(String sequence) {
        return db.queryForObject("SELECT "+sequence+".NEXTVAL FROM DUAL",Long.class);
    }

    private int count(String sql, Object... args) {
        return db.queryForObject(sql,Integer.class,args);
    }

    private int add(String user,long product,int quantity) {
        var request = new CartRequest();
        request.setPrdId(product);
        request.setQuantity(quantity);
        return cart.addItem(user,request).getQuantity();
    }

    private String order(String user,long product) {
        var item = new com.popq.platform.order.dto.OrderItemRequest();
        ReflectionTestUtils.setField(item,"prdId",product);
        ReflectionTestUtils.setField(item,"quantity",4);
        var request = new OrderCreateRequest();
        ReflectionTestUtils.setField(request,"items",List.of(item));
        try {
            orders.createOrder(user,request);
            return "CREATED";
        } catch (OrderException error) {
            return error.getErrorCode().name();
        }
    }

    private <T> List<T> parallel(Callable<T> first,Callable<T> second) throws Exception {
        var start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(10,TimeUnit.SECONDS); return first.call(); });
            var b = pool.submit(() -> { start.await(10,TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
}
