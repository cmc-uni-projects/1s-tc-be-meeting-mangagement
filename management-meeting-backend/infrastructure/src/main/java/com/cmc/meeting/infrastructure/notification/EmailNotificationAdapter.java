package com.cmc.meeting.infrastructure.notification;

import com.cmc.meeting.application.port.notification.EmailNotificationPort;
import com.cmc.meeting.application.port.service.AppConfigService;
import com.cmc.meeting.domain.model.Meeting;
import com.cmc.meeting.domain.model.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value; // Import Value
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class EmailNotificationAdapter implements EmailNotificationPort {

    private final JavaMailSender javaMailSender;
    private final ThymeleafEmailService thymeleafService;
    private final AppConfigService appConfigService;
    private final String frontendBaseUrl; // 1. Khai báo biến

    // 2. Inject giá trị từ application.yml vào Constructor
    public EmailNotificationAdapter(JavaMailSender javaMailSender,
                                    ThymeleafEmailService thymeleafService,
                                    AppConfigService appConfigService,
                                    @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.javaMailSender = javaMailSender;
        this.thymeleafService = thymeleafService;
        this.appConfigService = appConfigService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            javaMailSender.send(mimeMessage);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail HTML: " + e.getMessage());
        }
    }

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        try {
            String subject = "Yêu cầu Đặt lại Mật khẩu";
            
            // 3. Sử dụng biến frontendBaseUrl đã inject
            String currentFrontendUrl = appConfigService.getValue("frontend.url", this.frontendBaseUrl);
            
            String resetUrl = String.format("%s/reset-password?token=%s", currentFrontendUrl, token);

            Map<String, Object> variables = new HashMap<>();
            variables.put("userName", user.getFullName());
            variables.put("resetUrl", resetUrl);

            String htmlBody = thymeleafService.processTemplate("email.template.forgot-password", variables);
            this.sendHtmlEmail(user.getUsername(), subject, htmlBody);

        } catch (Exception e) {
            System.err.println("Lỗi gửi mail reset mật khẩu: " + e.getMessage());
        }
    }

    @Override
    public void sendWelcomeEmail(User user, String rawPassword) {
        final String TEMPLATE_KEY = "email.template.welcome";
        final String subject = "Chào mừng bạn đến với Hệ thống Đặt lịch họp";

        // 4. Sử dụng biến frontendBaseUrl
        String currentFrontendUrl = appConfigService.getValue("frontend.base-url", this.frontendBaseUrl);

        Map<String, Object> variables = new HashMap<>();
        variables.put("fullName", user.getFullName());
        variables.put("username", user.getUsername());
        variables.put("rawPassword", rawPassword);
        variables.put("loginUrl", currentFrontendUrl + "/login");

        String htmlBody = thymeleafService.processTemplate(TEMPLATE_KEY, variables);
        this.sendHtmlEmail(user.getUsername(), subject, htmlBody);
    }

    @Override
    public void sendMeetingReminder(String toEmail, Meeting meeting, String timeLabel) {
        try {
            final String TEMPLATE_KEY = "email.template.meeting-reminder";
            final String subject = "Nhắc nhở: Cuộc họp sắp diễn ra - " + meeting.getTitle();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
            
            // 5. Sử dụng biến frontendBaseUrl
            String currentFrontendUrl = appConfigService.getValue("frontend.base-url", this.frontendBaseUrl);
            
            String organizerName = (meeting.getOrganizer() != null) ? meeting.getOrganizer().getFullName() : "Ban tổ chức";

            Map<String, Object> variables = new HashMap<>();
            variables.put("title", meeting.getTitle());
            variables.put("timeLabel", timeLabel);
            variables.put("roomName", meeting.getRoom().getName());
            variables.put("startTime", meeting.getStartTime().format(formatter));
            variables.put("endTime", meeting.getEndTime().format(formatter));
            variables.put("organizer", organizerName);
            variables.put("meetingUrl", currentFrontendUrl + "/meetings/" + meeting.getId());

            String htmlBody = thymeleafService.processTemplate(TEMPLATE_KEY, variables);
            this.sendHtmlEmail(toEmail, subject, htmlBody);

        } catch (Exception e) {
            System.err.println("Lỗi gửi email nhắc nhở tới " + toEmail + ": " + e.getMessage());
        }
    }
}