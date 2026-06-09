package com.cmc.meeting.infrastructure.event;

import com.cmc.meeting.domain.model.User;
import com.cmc.meeting.domain.port.repository.UserRepository;
import com.cmc.meeting.infrastructure.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;

@Component
public class UserSyncListener {

    private static final Logger log = LoggerFactory.getLogger(UserSyncListener.class);
    private final UserRepository userRepository;

    public UserSyncListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveUserSync(Map<String, Object> userData) {
        try {
            String username = (String) userData.get("username");
            String fullName = (String) userData.get("fullName");
            
            // [CẢI THIỆN] Lấy Auth ID từ message
            // Lưu ý: JSON có thể gửi Integer, cần ép kiểu về Long
            Long authId = ((Integer) userData.get("auth_id")).longValue(); 

            log.info("🐰 RabbitMQ: Nhận yêu cầu đồng bộ user: {}, Auth ID: {}", username, authId);

            userRepository.findByUsername(username).ifPresentOrElse(existingUser -> {
                // Nếu User đã tồn tại, CẬP NHẬT Auth ID
                if (existingUser.getAuthServiceId() == null) {
                    existingUser.setAuthServiceId(authId);
                    userRepository.save(existingUser);
                    log.info("🔄 Đã cập nhật Auth ID: {} cho User '{}'.", authId, username);
                } else {
                    log.info("User '{}' đã tồn tại và có Auth ID, bỏ qua.", username);
                }
            }, () -> {
                // Nếu User chưa tồn tại, TẠO MỚI
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setFullName(fullName);
                // [CẢI THIỆN] Lưu Auth ID
                newUser.setAuthServiceId(authId); 
                newUser.setPassword("DUMMY_PASS_SYNCED"); 
                newUser.setActive(true);
                newUser.setRoles(new HashSet<>()); 

                userRepository.save(newUser);
                log.info("✅ Đã đồng bộ User '{}' với Auth ID {} vào Meeting DB.", username, authId);
            });
            
        } catch (Exception e) {
            log.error("❌ Lỗi khi đồng bộ user từ RabbitMQ", e);
        }
    }
}