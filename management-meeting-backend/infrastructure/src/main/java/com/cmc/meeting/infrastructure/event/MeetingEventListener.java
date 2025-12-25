package com.cmc.meeting.infrastructure.event;

import com.cmc.meeting.application.port.notification.EmailNotificationPort;
import com.cmc.meeting.domain.event.MeetingCancelledEvent;
import com.cmc.meeting.domain.event.MeetingCreatedEvent;
import com.cmc.meeting.domain.event.MeetingUpdatedEvent;
import com.cmc.meeting.domain.model.Meeting;
import com.cmc.meeting.domain.model.MeetingParticipant;
import com.cmc.meeting.domain.model.ParticipantStatus;
import com.cmc.meeting.domain.port.repository.MeetingRepository;
import com.cmc.meeting.infrastructure.notification.ThymeleafEmailService;
import com.cmc.meeting.infrastructure.persistence.jpa.adapter.GoogleCalendarAdapter;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class MeetingEventListener {

    private static final Logger log = LoggerFactory.getLogger(MeetingEventListener.class);
    
    // Inject từ application.yml -> app.backend.base-url
    @Value("${app.backend.base-url}")
    private String backendBaseUrl;
    
    // Inject từ application.yml -> app.frontend.base-url
    @Value("${app.frontend.base-url}") 
    private String frontendBaseUrl;

    private final EmailNotificationPort emailSender;
    private final MeetingRepository meetingRepository;
    private final ThymeleafEmailService thymeleafService;
    private final GoogleCalendarAdapter googleCalendarAdapter;

    public MeetingEventListener(EmailNotificationPort emailSender,
                                MeetingRepository meetingRepository,
                                ThymeleafEmailService thymeleafService,
                                GoogleCalendarAdapter googleCalendarAdapter) {
        this.emailSender = emailSender;
        this.meetingRepository = meetingRepository;
        this.thymeleafService = thymeleafService;
        this.googleCalendarAdapter = googleCalendarAdapter;
    }

    // --- 1. XỬ LÝ TẠO MỚI ---
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleMeetingCreation(MeetingCreatedEvent event) {
        log.info("EVENT RECEIVED [Async]: Xử lý sự kiện tạo cuộc họp (ID: {}).", event.getMeetingId());

        Meeting meeting;
        try {
            meeting = meetingRepository.findById(event.getMeetingId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy meeting ID: " + event.getMeetingId()));
        } catch (Exception e) {
            log.error("Lỗi Fatal: Không lấy được thông tin cuộc họp. Dừng xử lý.", e);
            return;
        }

        // Gửi Email
        try {
            sendInvitationEmails(meeting);
        } catch (Exception e) {
            log.error("Lỗi khi gửi Email thông báo: ", e);
        }

        // Đồng bộ Google Calendar
        try {
            String googleEventId = googleCalendarAdapter.pushMeetingToGoogle(
                meeting.getOrganizer().getId(), 
                meeting
            );

            if (googleEventId != null) {
                meeting.setGoogleEventId(googleEventId);
                meetingRepository.save(meeting); 
                log.info(">>> Đã lưu Google Event ID '{}' vào Database cho Meeting {}", googleEventId, meeting.getId());
            }
        } catch (Exception e) {
            log.error("Lỗi khi đồng bộ Google Calendar: ", e);
        }
    }

    // --- 2. XỬ LÝ CẬP NHẬT ---
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleMeetingUpdate(MeetingUpdatedEvent event) {
        log.info("EVENT: Cập nhật cuộc họp ID {}, Google ID: {}", event.getMeetingId(), event.getGoogleEventId());
        try {
            Meeting meeting = meetingRepository.findById(event.getMeetingId())
                    .orElseThrow(() -> new EntityNotFoundException("Meeting not found"));

            // A. Đồng bộ Google
            googleCalendarAdapter.updateMeetingOnGoogle(
                event.getUserId(), 
                event.getGoogleEventId(), 
                meeting
            );
            
            // B. Gửi Email thông báo cập nhật
            sendUpdateEmails(meeting);

        } catch (Exception e) {
            log.error("Lỗi handle update event: ", e);
        }
    }

    // --- 3. XỬ LÝ HỦY ---
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW) 
    public void handleMeetingCancellation(MeetingCancelledEvent event) {
        log.info("EVENT: Hủy cuộc họp ID {}, Google ID: {}", event.getMeetingId(), event.getGoogleEventId());
        try {
            // A. Đồng bộ Google (Xóa)
            if (event.getGoogleEventId() != null) {
                googleCalendarAdapter.deleteMeetingFromGoogle(
                    event.getUserId(), 
                    event.getGoogleEventId()
                );
            }
            
            // B. Gửi Email thông báo hủy
            Meeting meeting = meetingRepository.findById(event.getMeetingId())
                    .orElseThrow(() -> new EntityNotFoundException("Meeting not found"));
            
            sendCancellationEmails(meeting);

        } catch (Exception e) {
            log.error("Lỗi handle cancel event: ", e);
        }
    }

    private void sendInvitationEmails(Meeting meeting) {
        String subject = "Thư mời họp: " + meeting.getTitle();
        Map<String, Object> variables = buildCommonVariables(meeting);
        
        String internalTemplateKey = "email.template.internal";
        String respondEndpoint = "/meetings/respond-by-link";
        
        // Sử dụng Backend URL (API) cho link xác nhận tham gia
        String baseUrl = backendBaseUrl + respondEndpoint;

        if (meeting.getParticipants() != null) {
            for (MeetingParticipant p : meeting.getParticipants()) {
                if (p.getStatus() == ParticipantStatus.PENDING && p.getResponseToken() != null) {
                    variables.put("acceptUrl", String.format("%s?token=%s&status=ACCEPTED", baseUrl, p.getResponseToken()));
                    variables.put("declineUrl", String.format("%s?token=%s&status=DECLINED", baseUrl, p.getResponseToken()));
                    String htmlBody = thymeleafService.processTemplate(internalTemplateKey, variables);
                    emailSender.sendHtmlEmail(p.getUser().getUsername(), subject, htmlBody);
                }
            }
        }
        sendGuestEmails(meeting, subject, variables, "email.template.guest");
        log.info("Đã gửi email mời họp.");
    }

    private void sendUpdateEmails(Meeting meeting) {
        String subject = "CẬP NHẬT: " + meeting.getTitle();
        Map<String, Object> variables = buildCommonVariables(meeting);
        
        // --- SỬA ĐỔI QUAN TRỌNG ---
        // Sử dụng trực tiếp frontendBaseUrl từ cấu hình
        String link = frontendBaseUrl + "/meetings/" + meeting.getId();
        variables.put("meetingUrl", link);

        String templateKey = "email.template.meeting-update";
        String htmlBody = thymeleafService.processTemplate(templateKey, variables);

        // Gửi cho tất cả (trừ người từ chối)
        if (meeting.getParticipants() != null) {
            for (MeetingParticipant p : meeting.getParticipants()) {
                if (p.getStatus() != ParticipantStatus.DECLINED) {
                    emailSender.sendHtmlEmail(p.getUser().getUsername(), subject, htmlBody);
                }
            }
        }
        sendGuestEmails(meeting, subject, variables, templateKey);
        log.info("Đã gửi email cập nhật.");
    }

    private void sendCancellationEmails(Meeting meeting) {
        String subject = "HỦY HỌP: " + meeting.getTitle();
        Map<String, Object> variables = buildCommonVariables(meeting);
        
        variables.put("reason", meeting.getCancelReason() != null ? meeting.getCancelReason() : "Không có lý do cụ thể");

        String templateKey = "email.template.meeting-cancel";
        String htmlBody = thymeleafService.processTemplate(templateKey, variables);

        if (meeting.getParticipants() != null) {
            for (MeetingParticipant p : meeting.getParticipants()) {
                emailSender.sendHtmlEmail(p.getUser().getUsername(), subject, htmlBody);
            }
        }
        sendGuestEmails(meeting, subject, variables, templateKey);
        log.info("Đã gửi email hủy họp.");
    }

    private void sendGuestEmails(Meeting meeting, String subject, Map<String, Object> variables, String templateKey) {
        if (meeting.getGuestEmails() != null && !meeting.getGuestEmails().isEmpty()) {
            String htmlBody = thymeleafService.processTemplate(templateKey, variables);
            for (String guestEmail : meeting.getGuestEmails()) {
                emailSender.sendHtmlEmail(guestEmail, subject, htmlBody);
            }
        }
    }

    private Map<String, Object> buildCommonVariables(Meeting meeting) {
        Map<String, Object> variables = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        
        variables.put("title", meeting.getTitle());
        variables.put("startTime", meeting.getStartTime().format(formatter));
        variables.put("endTime", meeting.getEndTime().format(formatter));
        variables.put("roomName", meeting.getRoom().getName());
        variables.put("organizerName", meeting.getOrganizer().getFullName());
        return variables;
    }
}