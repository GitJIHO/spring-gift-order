package gift.service;

import gift.config.KakaoProperties;
import gift.dto.OrderDetailResponse;
import gift.dto.OrderRequest;
import gift.dto.OrderResponse;
import gift.entity.Option;
import gift.entity.Order;
import gift.entity.Product;
import gift.entity.User;
import gift.exception.OptionNotFoundException;
import gift.exception.ProductNotFoundException;
import gift.exception.UnauthorizedException;
import gift.repository.OptionRepository;
import gift.repository.OrderRepository;
import gift.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final UserRepository userRepository;
    private final KakaoMessageService kakaoMessageService;
    private final KakaoProperties kakaoProperties;

    public OrderService(OrderRepository orderRepository, OptionRepository optionRepository,
        UserRepository userRepository, KakaoMessageService kakaoMessageService,
        KakaoProperties kakaoProperties) {
        this.orderRepository = orderRepository;
        this.optionRepository = optionRepository;
        this.userRepository = userRepository;
        this.kakaoMessageService = kakaoMessageService;
        this.kakaoProperties = kakaoProperties;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("로그인된 유저가 없습니다."));
        Option option = optionRepository.findById(request.getOptionId())
            .orElseThrow(() -> new OptionNotFoundException("선택한 옵션이 존재하지 않습니다."));
        Product product = optionRepository.findProductByOptionId(request.getOptionId())
            .orElseThrow(() -> new ProductNotFoundException("해당 옵션을 가진 상품이 존재하지 않습니다."));

        Order order = orderRepository.save(
            new Order(option, user, request.getQuantity(), LocalDateTime.now(),
                request.getMessage()));

        option.subtractQuantity(request.getQuantity());
        user.subtractWishNumber(request.getQuantity(), product);

        if (kakaoProperties.checkKakaoLogin()) { //kakaologin을 수행하지 않으면 카카오 메시지를 보내지 않음
            kakaoMessageService.sendOrderMessage(order);
        }

        return new OrderResponse(order.getId(), option.getId(), request.getQuantity(),
            LocalDateTime.now(),
            request.getMessage());
    }

    public List<OrderDetailResponse> getAllOrders(Long userId) {
        List<Order> orders = orderRepository.findAllByUserId(userId);
        return orders.stream()
            .map(order -> new OrderDetailResponse(order.getId(), order.getOption(), order.getUser(),
                order.getQuantity(), order.getLocalDateTime(), order.getMessage()))
            .collect(Collectors.toList());

    }
}
