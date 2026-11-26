package com.popq.platform.cart;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.cart.dto.CartRequest;
import com.popq.platform.cart.entity.Cart;
import com.popq.platform.cart.exception.CartException;
import com.popq.platform.cart.repository.*;
import com.popq.platform.cart.service.CartService;
import com.popq.platform.product.dto.ProductResponse;
import com.popq.platform.product.service.ProductService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTest {
    @Test void mergesAtBoundaryWithoutWritingStock() { check(4, 4, 0, null); }
    @Test void rejectsStockOverflow() { check(3, 10, 0, "OUT_OF_STOCK"); }
    @Test void rejectsLimitIncludingPastPurchases() { check(10, 4, 1, "PURCHASE_LIMIT_EXCEEDED"); }

    private void check(int stock, int limit, long purchased, String errorCode) {
        var carts = mock(CartRepository.class);
        var members = mock(CartMemberRepository.class);
        var history = mock(CartPurchaseHistoryRepository.class);
        var products = mock(ProductService.class);
        var service = new CartService(carts, members, history, mock(UserRepository.class), products);
        var user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(members.findByUsernameForUpdate("tester")).thenReturn(Optional.of(user));
        when(history.findPurchasedQuantities(1L)).thenReturn(Map.of(2L, purchased));
        var cart = new Cart();
        cart.setMemberId(1L);
        cart.setPrdId(2L);
        cart.setQuantity(2);
        when(carts.findByMemberIdAndPrdId(1L, 2L)).thenReturn(Optional.of(cart));
        when(products.listProductByIds(List.of(2L))).thenReturn(List.of(ProductResponse.builder()
                .prdId(2L).stock(stock).maxQty(limit).price(1000).saleStatus("ON_SALE").build()));
        var request = new CartRequest();
        request.setPrdId(2L);
        request.setQuantity(2);
        if (errorCode == null) {
            when(carts.saveAndFlush(cart)).thenReturn(cart);
            var result = service.addItem("tester", request);
            assertEquals(4, result.getQuantity());
            assertEquals(stock, result.getStock());
            verify(carts).saveAndFlush(cart);
        } else {
            var error = assertThrows(CartException.class, () -> service.addItem("tester", request));
            assertEquals(errorCode, error.getCode());
            assertEquals(2, cart.getQuantity());
            verify(carts, never()).saveAndFlush(any());
        }
        verify(products).listProductByIds(List.of(2L));
        verifyNoMoreInteractions(products);
    }
}
