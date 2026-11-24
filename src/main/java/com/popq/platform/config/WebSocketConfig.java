package com.popq.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * WebSocket 메세지 브로커 설정
     *
     * - /topic : 여러 사용자에게 메세지를 전달하는 공개 구독 경로
     *      ex) /topic/timeslots/1
     *
     * - /queue : 특정 사용자 또는 개병 메세지 전달에 사용할 수 있는 경로
     *
     * - /app : 클라이언트가 서버의 @MessageMapping 메서드로 메세지를 보낼 때 사용하느 prefix
     **/
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 서버 -> 클라이언트 메시지 전달을 담당하는 Simple Broker 활성화
        config.enableSimpleBroker("/topic", "/queue");
        // 클라이언트 → 서버로 메시지를 보낼 때 사용할 공통 prefix 설정
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 최초 연결 Endpoint 등록
        // ex) ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                // React 개발 서버에서의 WebSocket 연결 허용
                .setAllowedOriginPatterns("*");
    }
}
