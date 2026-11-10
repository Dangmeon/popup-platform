package com.popq.platform.service;

import com.popq.platform.dto.CartRequest;
import com.popq.platform.dto.CartResponse;
import com.popq.platform.dto.ProductResponse;
import com.popq.platform.entity.Cart;
import com.popq.platform.entity.User;
import com.popq.platform.exception.CartException;
import com.popq.platform.repository.CartMemberRepository;
import com.popq.platform.repository.CartPurchaseHistoryRepository;
import com.popq.platform.repository.CartRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartMemberRepository cartMemberRepository;
    private final CartPurchaseHistoryRepository purchaseHistoryRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    // 내 장바구니 조회
    public List<CartResponse> getCart(String username) {
        User user = findMember(username, false);

        List<Cart> carts =
                cartRepository.findByMemberIdOrderByAddedAtDescCartIdDesc(
                        user.getId()
                );

        if (carts.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = carts.stream()
                .map(Cart::getPrdId)
                .distinct()
                .toList();

        Map<Long, ProductResponse> products =
                productService.listProductByIds(productIds).stream()
                        .collect(Collectors.toMap(
                                ProductResponse::getPrdId,
                                Function.identity()
                        ));

        Map<Long, Long> purchasedQuantities =
                purchaseHistoryRepository.findPurchasedQuantities(
                        user.getId()
                );

        return carts.stream()
                .map(cart -> toResponse(
                        cart,
                        products.get(cart.getPrdId()),
                        purchasedQuantities.getOrDefault(
                                cart.getPrdId(),
                                0L
                        )
                ))
                .toList();
    }

    // 상품 추가
    @Transactional
    public CartResponse addItem(
            String username,
            CartRequest request
    ) {
        validateId(request.getPrdId(), "상품 번호");
        validateQuantity(request.getQuantity());

        User user = findMember(username, true);
        ProductResponse product = findProduct(request.getPrdId());

        Cart cart = cartRepository
                .findByMemberIdAndPrdId(
                        user.getId(),
                        request.getPrdId()
                )
                .orElse(null);

        long combinedQuantity = request.getQuantity();

        if (cart != null) {
            validateQuantity(cart.getQuantity());
            combinedQuantity += cart.getQuantity();
        }

        if (combinedQuantity > Integer.MAX_VALUE) {
            throw new CartException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "담을 수 있는 수량 범위를 초과했습니다."
            );
        }

        int quantity = (int) combinedQuantity;
        long purchasedQuantity = getPurchasedQuantity(
                user.getId(),
                request.getPrdId()
        );

        validatePurchasable(product, quantity, purchasedQuantity);

        if (cart == null) {
            cart = new Cart();
            cart.setMemberId(user.getId());
            cart.setPrdId(request.getPrdId());
        }

        cart.setQuantity(quantity);

        Cart saved = cartRepository.saveAndFlush(cart);

        return toResponse(saved, product, purchasedQuantity);
    }

    // 상품 수량 변경
    @Transactional
    public CartResponse updateQuantity(
            String username,
            Long cartItemId,
            Integer quantity
    ) {
        validateId(cartItemId, "장바구니 항목 번호");
        validateQuantity(quantity);

        User user = findMember(username, true);
        Cart cart = findOwnedCart(cartItemId, user.getId());
        ProductResponse product = findProduct(cart.getPrdId());

        long purchasedQuantity = getPurchasedQuantity(
                user.getId(),
                cart.getPrdId()
        );

        validatePurchasable(product, quantity, purchasedQuantity);

        cart.setQuantity(quantity);
        cartRepository.flush();

        return toResponse(cart, product, purchasedQuantity);
    }

    // 상품 삭제
    @Transactional
    public void removeItem(
            String username,
            Long cartItemId
    ) {
        validateId(cartItemId, "장바구니 항목 번호");

        User user = findMember(username, true);
        Cart cart = findOwnedCart(cartItemId, user.getId());

        cartRepository.delete(cart);
        cartRepository.flush();
    }

    // 로그인 회원 조회
    private User findMember(
            String username,
            boolean lock
    ) {
        if (username == null || username.isBlank()) {
            throw new CartException(
                    HttpStatus.UNAUTHORIZED,
                    "LOGIN_REQUIRED",
                    "로그인이 필요합니다."
            );
        }

        return (lock
                ? cartMemberRepository.findByUsernameForUpdate(username)
                : userRepository.findByUsername(username))
                .orElseThrow(() -> new CartException(
                        HttpStatus.UNAUTHORIZED,
                        "MEMBER_NOT_FOUND",
                        "로그인한 회원 정보를 찾을 수 없습니다."
                ));
    }

    // 상품 조회
    private ProductResponse findProduct(Long prdId) {
        return productService.listProductByIds(List.of(prdId))
                .stream()
                .filter(product -> prdId.equals(product.getPrdId()))
                .findFirst()
                .orElseThrow(() -> new CartException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "상품 정보를 찾을 수 없습니다."
                ));
    }

    // 항목 소유자 확인
    private Cart findOwnedCart(
            Long cartItemId,
            Long memberId
    ) {
        Cart cart = cartRepository.findById(cartItemId)
                .orElseThrow(() -> new CartException(
                        HttpStatus.NOT_FOUND,
                        "CART_ITEM_NOT_FOUND",
                        "장바구니 항목을 찾을 수 없습니다."
                ));

        if (!memberId.equals(cart.getMemberId())) {
            throw new CartException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "본인의 장바구니만 변경할 수 있습니다."
            );
        }

        return cart;
    }

    // 누적 주문 수량 조회
    private long getPurchasedQuantity(
            Long memberId,
            Long prdId
    ) {
        return purchaseHistoryRepository
                .findPurchasedQuantities(memberId)
                .getOrDefault(prdId, 0L);
    }

    // 번호 검증
    private void validateId(Long id, String label) {
        if (id == null || id < 1) {
            throw new CartException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ID",
                    label + "는 1 이상이어야 합니다."
            );
        }
    }

    // 수량 검증
    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new CartException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "수량은 1개 이상이어야 합니다."
            );
        }
    }

    // 구매 가능 여부 검증
    private void validatePurchasable(
            ProductResponse product,
            Integer quantity,
            long purchasedQuantity
    ) {
        CartException restriction = findRestriction(
                product,
                quantity,
                purchasedQuantity
        );

        if (restriction != null) {
            throw restriction;
        }
    }

    // 구매 불가 사유
    private CartException findRestriction(
            ProductResponse product,
            Integer quantity,
            long purchasedQuantity
    ) {
        if (product == null) {
            return new CartException(
                    HttpStatus.NOT_FOUND,
                    "PRODUCT_NOT_FOUND",
                    "삭제되었거나 존재하지 않는 상품입니다."
            );
        }

        if (quantity == null || quantity < 1) {
            return new CartException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "수량은 1개 이상이어야 합니다."
            );
        }

        if (
                product.getPrice() == null ||
                        product.getPrice() < 0 ||
                        product.getStock() == null ||
                        product.getStock() < 0 ||
                        product.getMaxQty() == null ||
                        product.getMaxQty() < 1 ||
                        purchasedQuantity < 0
        ) {
            return new CartException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_INFO_INVALID",
                    "상품의 가격·재고·구매 제한 정보를 확인해 주세요."
            );
        }

        if ("SOLD_OUT".equals(product.getSaleStatus())) {
            return new CartException(
                    HttpStatus.CONFLICT,
                    "OUT_OF_STOCK",
                    "품절된 상품입니다."
            );
        }

        if (!"ON_SALE".equals(product.getSaleStatus())) {
            return new CartException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_NOT_ON_SALE",
                    "현재 판매 중인 상품이 아닙니다."
            );
        }

        if (quantity > product.getStock()) {
            return new CartException(
                    HttpStatus.BAD_REQUEST,
                    "OUT_OF_STOCK",
                    "현재 재고보다 많은 수량을 담을 수 없습니다."
            );
        }

        if (
                purchasedQuantity >
                        (long) product.getMaxQty() - quantity
        ) {
            return new CartException(
                    HttpStatus.BAD_REQUEST,
                    "PURCHASE_LIMIT_EXCEEDED",
                    "기존 주문과 장바구니 수량의 합이 회원 누적 구매 제한을 초과합니다."
            );
        }

        return null;
    }

    // 화면 응답 생성
    private CartResponse toResponse(
            Cart cart,
            ProductResponse product,
            long purchasedQuantity
    ) {
        CartException restriction = findRestriction(
                product,
                cart.getQuantity(),
                purchasedQuantity
        );

        Long itemTotalPrice = null;

        if (
                product != null &&
                        product.getPrice() != null &&
                        product.getPrice() >= 0 &&
                        cart.getQuantity() != null &&
                        cart.getQuantity() > 0
        ) {
            itemTotalPrice =
                    (long) product.getPrice() * cart.getQuantity();
        }

        return CartResponse.builder()
                .cartId(cart.getCartId())
                .prdId(cart.getPrdId())
                .quantity(cart.getQuantity())
                .addedAt(cart.getAddedAt())
                .popId(product != null ? product.getPopId() : null)
                .productName(
                        product != null ? product.getProductName() : null
                )
                .productImg(
                        product != null ? product.getProductImg() : null
                )
                .price(product != null ? product.getPrice() : null)
                .stock(product != null ? product.getStock() : null)
                .maxQty(product != null ? product.getMaxQty() : null)
                .saleStatus(
                        product != null ? product.getSaleStatus() : null
                )
                .purchasedQuantity(purchasedQuantity)
                .itemTotalPrice(itemTotalPrice)
                .purchasable(restriction == null)
                .unavailableReason(
                        restriction != null
                                ? restriction.getMessage()
                                : null
                )
                .build();
    }
}