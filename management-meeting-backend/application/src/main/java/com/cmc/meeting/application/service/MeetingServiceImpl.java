package com.cmc.meeting.application.service;

// Imports cho DTOs
import com.cmc.meeting.application.dto.meeting.CheckInRequest;
import com.cmc.meeting.application.dto.meeting.MeetingCancelRequest;
import com.cmc.meeting.application.dto.meeting.MeetingResponseRequest;
import com.cmc.meeting.application.dto.request.MeetingCreationRequest;
import com.cmc.meeting.application.dto.request.MeetingUpdateRequest;
import com.cmc.meeting.application.dto.response.BookedSlotDTO;
import com.cmc.meeting.application.dto.response.MeetingDTO;
import com.cmc.meeting.application.dto.response.MeetingParticipantDTO;
import com.cmc.meeting.application.dto.recurrence.FrequencyType;
import com.cmc.meeting.application.dto.recurrence.RecurrenceRuleDTO;
import com.cmc.meeting.application.dto.timeslot.TimeSlotDTO;

// Imports cho Ports
import com.cmc.meeting.application.port.service.MeetingService;
import com.cmc.meeting.application.port.service.NotificationService;
import com.cmc.meeting.domain.port.repository.DeviceRepository;
import com.cmc.meeting.domain.port.repository.MeetingRepository;
import com.cmc.meeting.domain.port.repository.RoomRepository;
import com.cmc.meeting.domain.port.repository.UserRepository;

// Imports cho Domain & Events (QUAN TRỌNG)
import com.cmc.meeting.domain.event.MeetingCancelledEvent; // Import mới
import com.cmc.meeting.domain.event.MeetingCreatedEvent;
import com.cmc.meeting.domain.event.MeetingUpdatedEvent;   // Import mới
import com.cmc.meeting.domain.exception.MeetingConflictException;
import com.cmc.meeting.domain.exception.PolicyViolationException;
import com.cmc.meeting.domain.model.*;

// Imports cho Java & Spring
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class MeetingServiceImpl implements MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingServiceImpl.class);

    private final MeetingRepository meetingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ModelMapper modelMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    public MeetingServiceImpl(MeetingRepository meetingRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository,
                              ModelMapper modelMapper,
                              ApplicationEventPublisher eventPublisher,
                              DeviceRepository deviceRepository,
                              NotificationService notificationService) {
        this.meetingRepository = meetingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.eventPublisher = eventPublisher;
        this.deviceRepository = deviceRepository;
        this.notificationService = notificationService;
    }

    /**
     * (US-1) Tạo cuộc họp
     */
    @Override
    public MeetingDTO createMeeting(MeetingCreationRequest request, Long currentUserId) {
        if (request.getStartTime() != null) {
            request.setStartTime(request.getStartTime().withSecond(0).withNano(0));
        }
        if (request.getEndTime() != null) {
            request.setEndTime(request.getEndTime().withSecond(0).withNano(0));
        }
        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người tạo (creator)"));

        User organizer;
        if (request.getOnBehalfOfUserId() != null) {
            organizer = userRepository.findById(request.getOnBehalfOfUserId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người tổ chức (onBehalfOf)"));
        } else {
            organizer = creator;
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phòng họp"));

        Set<User> participants = request.getParticipantIds().stream()
                .map(id -> userRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người tham dự với ID: " + id)))
                .collect(Collectors.toSet());

        Set<Device> devices = request.getDeviceIds().stream()
                .map(id -> deviceRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thiết bị với ID: " + id)))
                .collect(Collectors.toSet());
        Set<String> guestEmails = (request.getGuestEmails() != null) ? request.getGuestEmails() : new HashSet<>();

        if (request.getRecurrenceRule() == null) {
            checkAccessAndConflicts(room, organizer, participants, devices,
                    request.getStartTime(), request.getEndTime(), null);

            return createSingleMeeting(request, room, creator, organizer,
                    participants, devices, guestEmails, null);
        } else {
            String seriesId = UUID.randomUUID().toString();
            List<TimeSlotDTO> slots = calculateRecurrenceSlots(
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getRecurrenceRule());

            for (TimeSlotDTO slot : slots) {
                checkAccessAndConflicts(room, organizer, participants, devices, slot.getStartTime(), slot.getEndTime(),
                        null);
            }

            List<MeetingDTO> createdMeetings = new ArrayList<>();
            for (TimeSlotDTO slot : slots) {
                MeetingCreationRequest singleRequest = modelMapper.map(request, MeetingCreationRequest.class);
                singleRequest.setStartTime(slot.getStartTime());
                singleRequest.setEndTime(slot.getEndTime());

                createdMeetings.add(
                        createSingleMeeting(singleRequest, room, creator, organizer, participants, devices, guestEmails,
                                seriesId));
            }
            return createdMeetings.get(0);
        }
    }

    /**
     * (US-2) Hủy lịch họp (Có đồng bộ Google)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelMeeting(Long meetingId, MeetingCancelRequest request, Long currentUserId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp với ID: " + meetingId));

        boolean isAdmin = userRepository.findById(currentUserId)
                .map(u -> u.getRoles().contains(Role.ROLE_ADMIN))
                .orElse(false);

        if (!meeting.getOrganizer().getId().equals(currentUserId) && !isAdmin) {
            throw new PolicyViolationException("Chỉ người tổ chức hoặc Admin mới có quyền hủy cuộc họp này.");
        }

        meeting.cancelMeeting(request.getReason());
        Meeting savedMeeting = meetingRepository.save(meeting);

        // 1. Bắn sự kiện hủy để Google Calendar Adapter lắng nghe và xóa lịch
        if (savedMeeting.getGoogleEventId() != null) {
            eventPublisher.publishEvent(new MeetingCancelledEvent(
                    savedMeeting.getId(),
                    savedMeeting.getOrganizer().getId(),
                    savedMeeting.getGoogleEventId()
            ));
        }

        // 2. Gửi thông báo In-App
        String message = String.format("Cuộc họp '%s' (lúc %s) đã bị hủy.",
                savedMeeting.getTitle(), savedMeeting.getStartTime().toLocalDate());
        
        savedMeeting.getParticipants().stream()
                .filter(p -> !p.getUser().getId().equals(currentUserId))
                .forEach(p -> notificationService.createNotification(p.getUser(), message));
    }

    /**
     * (US-2) Sửa lịch họp (Có đồng bộ Google)
     */
    @Override
    public MeetingDTO updateMeeting(Long meetingId, MeetingUpdateRequest request, Long currentUserId) {
        if (request.getStartTime() != null) {
            request.setStartTime(request.getStartTime().withSecond(0).withNano(0));
        }
        if (request.getEndTime() != null) {
            request.setEndTime(request.getEndTime().withSecond(0).withNano(0));
        }

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp với ID: " + meetingId));

        if (!meeting.getOrganizer().getId().equals(currentUserId)) {
            throw new PolicyViolationException("Chỉ người tổ chức mới có quyền sửa cuộc họp này.");
        }

        if (meeting.getStatus() == BookingStatus.CANCELLED) {
            throw new PolicyViolationException("Không thể sửa cuộc họp đã bị hủy.");
        }
        if (meeting.getStartTime().isBefore(LocalDateTime.now())) {
            throw new PolicyViolationException("Không thể sửa cuộc họp đã diễn ra.");
        }

        Room newRoom = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phòng họp"));

        boolean isRoomChanged = !meeting.getRoom().getId().equals(newRoom.getId());
        boolean isTimeChanged = !meeting.getStartTime().equals(request.getStartTime()) ||
                !meeting.getEndTime().equals(request.getEndTime());

        Set<User> newParticipantUsers = request.getParticipantIds().stream()
                .map(id -> userRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người tham dự với ID: " + id)))
                .collect(Collectors.toSet());

        Set<Device> newDevices = request.getDeviceIds().stream()
                .map(id -> deviceRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy thiết bị với ID: " + id)))
                .collect(Collectors.toSet());
        Set<String> newGuestEmails = (request.getGuestEmails() != null) ? request.getGuestEmails() : new HashSet<>();

        checkAccessAndConflicts(newRoom, meeting.getOrganizer(), newParticipantUsers, newDevices,
                request.getStartTime(), request.getEndTime(), meetingId);

        Set<MeetingParticipant> newParticipants = new HashSet<>();
        User organizer = meeting.getOrganizer();
        newParticipants.add(new MeetingParticipant(organizer, ParticipantStatus.ACCEPTED, null));
        newParticipantUsers.forEach(user -> {
            if (!user.getId().equals(organizer.getId())) {
                newParticipants.add(
                        new MeetingParticipant(user, ParticipantStatus.PENDING, UUID.randomUUID().toString()));
            }
        });

        meeting.setTitle(request.getTitle());
        meeting.setDescription(request.getDescription());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());
        meeting.setRoom(newRoom);
        meeting.setParticipants(newParticipants);
        meeting.setDevices(newDevices);
        meeting.setGuestEmails(newGuestEmails);

        if (newRoom.isRequiresApproval()) {
            if (isRoomChanged || isTimeChanged) {
                meeting.setStatus(BookingStatus.PENDING_APPROVAL);
                notifyAdminsForApproval(meeting, organizer);
                notificationService.createNotification(organizer,
                        "Do thay đổi phòng/giờ sang khu vực cần duyệt, lịch họp của bạn đã chuyển sang trạng thái CHỜ DUYỆT.",
                        meeting);
            }
        } else {
            meeting.setStatus(BookingStatus.CONFIRMED);
        }

        Meeting updatedMeeting = meetingRepository.save(meeting);

        if (updatedMeeting.getStatus() == BookingStatus.CONFIRMED) {
            String updateMsg = "Thông tin cuộc họp '" + updatedMeeting.getTitle() + "' đã được cập nhật.";
            meeting.getParticipants().stream()
                    .filter(p -> !p.getUser().getId().equals(currentUserId))
                    .forEach(p -> notificationService.createNotification(p.getUser(), updateMsg, updatedMeeting));
            
            // === LOGIC MỚI: CẬP NHẬT LÊN GOOGLE CALENDAR ===
            if (updatedMeeting.getGoogleEventId() != null) {
                eventPublisher.publishEvent(new MeetingUpdatedEvent(
                    updatedMeeting.getId(),
                    updatedMeeting.getOrganizer().getId(),
                    updatedMeeting.getGoogleEventId()
                ));
            }
        }

        return convertMeetingToDTO(updatedMeeting);
    }

    /**
     * (US-3) Hủy chuỗi lịch định kỳ (Có đồng bộ Google)
     */
    @Override
    public void cancelMeetingSeries(String seriesId, MeetingCancelRequest request, Long currentUserId) {
        List<Meeting> meetingsInSeries = meetingRepository.findAllBySeriesId(seriesId);

        if (meetingsInSeries.isEmpty()) {
            throw new EntityNotFoundException("Không tìm thấy chuỗi cuộc họp.");
        }

        Meeting firstMeeting = meetingsInSeries.get(0);
        if (!firstMeeting.getOrganizer().getId().equals(currentUserId)) {
            throw new PolicyViolationException("Chỉ người tổ chức mới có quyền hủy chuỗi họp này.");
        }

        log.info("Hủy chuỗi {}: Tìm thấy {} cuộc họp.", seriesId, meetingsInSeries.size());

        for (Meeting meeting : meetingsInSeries) {
            // Thêm điều kiện OR PENDING_APPROVAL
            boolean isFuture = meeting.getStartTime().isAfter(LocalDateTime.now());
            boolean isActive = meeting.getStatus() == BookingStatus.CONFIRMED || 
                               meeting.getStatus() == BookingStatus.PENDING_APPROVAL;

            if (isFuture && isActive) {
                log.info("-> Đang hủy Meeting ID: {}", meeting.getId());
                meeting.cancelMeeting(request.getReason());
                meetingRepository.save(meeting);

                // Xóa trên Google (chỉ nếu đã sync)
                if (meeting.getGoogleEventId() != null) {
                    eventPublisher.publishEvent(new MeetingCancelledEvent(
                        meeting.getId(), 
                        meeting.getOrganizer().getId(), 
                        meeting.getGoogleEventId()
                    ));
                }
            }
        }
    }

    /**
     * (BS-2.1) Cập nhật chuỗi lịch định kỳ (Có đồng bộ Google)
     */
    @Override
    public MeetingDTO updateMeetingSeries(String seriesId, MeetingCreationRequest request, Long currentUserId) {

        List<Meeting> meetingsInSeries = meetingRepository.findAllBySeriesId(seriesId);
        if (meetingsInSeries.isEmpty()) {
            throw new EntityNotFoundException("Không tìm thấy chuỗi cuộc họp.");
        }

        Meeting firstMeeting = meetingsInSeries.get(0);
        if (!firstMeeting.getOrganizer().getId().equals(currentUserId)) {
            throw new PolicyViolationException("Chỉ người tổ chức mới có quyền sửa chuỗi họp này.");
        }
        
        // (Bổ sung) Validate request
        if (request.getRecurrenceRule() == null) {
             throw new IllegalArgumentException("Cập nhật chuỗi yêu cầu phải có rule lặp lại.");
        }

        String reason = "Cuộc họp định kỳ đã được cập nhật hoặc thay đổi.";
        for (Meeting meeting : meetingsInSeries) {
            //  Thêm điều kiện OR PENDING_APPROVAL
            boolean isFuture = meeting.getStartTime().isAfter(LocalDateTime.now());
            boolean isActive = meeting.getStatus() == BookingStatus.CONFIRMED || 
                               meeting.getStatus() == BookingStatus.PENDING_APPROVAL;

            if (isFuture && isActive) {
                log.info("-> (Update) Hủy Meeting ID: {}", meeting.getId());
                meeting.cancelMeeting(reason);
                meetingRepository.save(meeting);

                // Xóa trên Google
                if (meeting.getGoogleEventId() != null) {
                    eventPublisher.publishEvent(new MeetingCancelledEvent(
                        meeting.getId(), 
                        meeting.getOrganizer().getId(), 
                        meeting.getGoogleEventId()
                    ));
                }
            }
        }

        log.info("-> (Update) Đang tạo chuỗi họp mới thay thế...");
        return this.createMeeting(request, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingDTO getMeetingById(Long meetingId, Long currentUserId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp với ID: " + meetingId));

        boolean isOrganizer = meeting.getOrganizer().getId().equals(currentUserId);
        boolean isParticipant = meeting.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(currentUserId));

        if (!isOrganizer && !isParticipant) {
            throw new PolicyViolationException("Bạn không có quyền xem chi tiết cuộc họp này.");
        }

        return convertMeetingToDTO(meeting);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingDTO> getMyMeetings(Long currentUserId) {
        List<Meeting> meetings = meetingRepository.findAllByUserId(currentUserId);
        return meetings.stream()
                .map(this::convertMeetingToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void respondToInvitation(Long meetingId, MeetingResponseRequest request, Long currentUserId) {
        if (request.getStatus() == ParticipantStatus.PENDING) {
            throw new IllegalArgumentException("Không thể đổi trạng thái về PENDING.");
        }

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp với ID: " + meetingId));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy user"));

        meeting.respondToInvitation(currentUser, request.getStatus());
        Meeting savedMeeting = meetingRepository.save(meeting);

        String message = String.format("%s đã %s lời mời tham gia cuộc họp '%s'.",
                currentUser.getFullName(),
                request.getStatus() == ParticipantStatus.ACCEPTED ? "chấp nhận" : "từ chối",
                savedMeeting.getTitle());

        notificationService.createNotification(savedMeeting.getOrganizer(), message);
    }

    @Override
    public String checkIn(CheckInRequest request, Long currentUserId) {
        Meeting meeting = meetingRepository.findCheckInEligibleMeeting(
                currentUserId, request.getRoomId(), LocalDateTime.now())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy cuộc họp hợp lệ để check-in tại phòng này cho bạn."));

        meeting.checkIn();
        meetingRepository.save(meeting);
        return String.format("Check-in thành công cho cuộc họp: %s", meeting.getTitle());
    }

    @Override
    public String respondByLink(String token, ParticipantStatus status) {
        if (status == ParticipantStatus.PENDING) return "Trạng thái không hợp lệ.";

        Meeting meeting = meetingRepository.findMeetingByParticipantToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Link phản hồi không hợp lệ hoặc đã hết hạn."));

        MeetingParticipant participant = meeting.getParticipants().stream()
                .filter(p -> token.equals(p.getResponseToken()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người tham dự với token này."));

        participant.setStatus(status);
        participant.setResponseToken(null);
        meetingRepository.save(meeting);

        return status == ParticipantStatus.ACCEPTED 
            ? "Cảm ơn! Phản hồi (Chấp nhận) của bạn đã được ghi lại."
            : "Phản hồi (Từ chối) của bạn đã được ghi lại.";
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeetingDTO> getMyMeetings(Long currentUserId, Pageable pageable) {
        Page<Meeting> meetings = meetingRepository.findAllByUserId(currentUserId, pageable);
        return meetings.map(this::convertMeetingToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MeetingDTO> getAllMeetings(Pageable pageable) {
        Page<Meeting> meetingsPage = meetingRepository.findAllMeetings(pageable);
        return meetingsPage.map(this::convertMeetingToDTO);
    }

    @Override
    public void processMeetingApproval(Long meetingId, boolean isApproved, String reason, Long currentAdminId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp với ID: " + meetingId));

        if (meeting.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new PolicyViolationException("Cuộc họp này không ở trạng thái chờ duyệt.");
        }

        if (isApproved) {
            // 1. Kiểm tra lại lần cuối xem có ai nhanh tay CONFIRMED trước đó không (Double-check)
            boolean hasConflict = meetingRepository.existsConfirmedMeetingInTimeRange(
                    meeting.getRoom().getId(), meeting.getStartTime(), meeting.getEndTime(), meeting.getId());
            
            if (hasConflict) {
                throw new MeetingConflictException("Đã có một cuộc họp khác được duyệt (CONFIRMED) trong khung giờ này rồi!");
            }

            // 2. Chốt lịch này (Winner)
            meeting.setStatus(BookingStatus.CONFIRMED);
            meetingRepository.save(meeting);
            
            // Trigger Google Sync & Events
            eventPublisher.publishEvent(new MeetingCreatedEvent(meeting.getId())); 

            // Thông báo cho Organizer của lịch được duyệt
            notificationService.createNotification(meeting.getOrganizer(),
                    "Phòng họp '" + meeting.getRoom().getName() + "' đã được phê duyệt!", meeting);

            // Gửi lời mời cho người tham gia
            String inviteMsg = String.format("%s đã mời bạn tham gia cuộc họp: %s",
                    meeting.getOrganizer().getFullName(), meeting.getTitle());
            sendNotificationsToParticipants(meeting, inviteMsg);

            // === [LOGIC MỚI] 3. TỰ ĐỘNG HỦY CÁC LỊCH PENDING TRÙNG (Losers) ===
            // Tìm các lịch PENDING khác cùng phòng, cùng giờ
            List<Meeting> pendingConflicts = meetingRepository.findPendingConflicts(
                    meeting.getRoom().getId(), meeting.getStartTime(), meeting.getEndTime(), meeting.getId());

            if (!pendingConflicts.isEmpty()) {
                log.info("Admin duyệt meeting {}. Hệ thống tự động từ chối {} yêu cầu khác bị trùng.", meetingId, pendingConflicts.size());
                
                String autoRejectReason = String.format(
                        "Hệ thống tự động từ chối do Admin đã duyệt ưu tiên cho cuộc họp: '%s' (%s - %s)",
                        meeting.getTitle(), 
                        meeting.getStartTime().toLocalTime(), 
                        meeting.getEndTime().toLocalTime());

                for (Meeting conflictingMeeting : pendingConflicts) {
                    conflictingMeeting.setStatus(BookingStatus.REJECTED);
                    conflictingMeeting.setCancelReason(autoRejectReason); 
                    meetingRepository.save(conflictingMeeting);

                    // Gửi thông báo cho người bị từ chối
                    notificationService.createNotification(
                            conflictingMeeting.getOrganizer(),
                            "Yêu cầu đặt phòng '" + meeting.getRoom().getName() + "' của bạn bị TỪ CHỐI TỰ ĐỘNG. Lý do: Đã duyệt cho người khác.",
                            conflictingMeeting
                    );
                }
            }

        } else {
            // Trường hợp Admin từ chối thủ công
            meeting.setStatus(BookingStatus.REJECTED);
            meetingRepository.save(meeting);
            String rejectMsg = String.format("Yêu cầu đặt phòng '%s' bị từ chối. Lý do: %s",
                    meeting.getRoom().getName(), reason);
            notificationService.createNotification(meeting.getOrganizer(), rejectMsg);
        }
    }

    // Helpers
    private MeetingDTO createSingleMeeting(MeetingCreationRequest request, Room room,
            User creator, User organizer,
            Set<User> participants, Set<Device> devices, Set<String> guestEmails, String seriesId) {

        BookingStatus initialStatus = room.isRequiresApproval() 
            ? BookingStatus.PENDING_APPROVAL 
            : BookingStatus.CONFIRMED;

        Meeting newMeeting = new Meeting(
                request.getTitle(), request.getStartTime(), request.getEndTime(),
                room, creator, organizer, participants, devices, guestEmails, seriesId);
        newMeeting.setCheckinCode(UUID.randomUUID().toString());
        newMeeting.setStatus(initialStatus);

        Meeting savedMeeting = meetingRepository.save(newMeeting);

        if (initialStatus == BookingStatus.CONFIRMED) {
            eventPublisher.publishEvent(new MeetingCreatedEvent(savedMeeting.getId())); // Trigger Google Sync
            String message = String.format("%s đã mời bạn tham gia cuộc họp: %s",
                    creator.getFullName(), savedMeeting.getTitle());
            sendNotificationsToParticipants(savedMeeting, message);
        } else {
            String msgToOrganizer = String.format("Yêu cầu đặt phòng '%s' của bạn đang chờ Admin phê duyệt.", room.getName());
            notificationService.createNotification(organizer, msgToOrganizer, savedMeeting);

            List<User> admins = userRepository.findAllAdmins();
            String msgToAdmin = String.format("Yêu cầu duyệt phòng mới: %s muốn đặt phòng '%s' vào lúc %s.",
                    organizer.getFullName(), room.getName(), savedMeeting.getStartTime().toString().replace("T", " "));
            for (User admin : admins) {
                notificationService.createNotification(admin, msgToAdmin, savedMeeting);
            }
        }
        return convertMeetingToDTO(savedMeeting);
    }

    private void checkAccessAndConflicts(Room room, User organizer, Set<User> participants, Set<Device> devices,
            LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore) {
        
        // 1. Kiểm tra trạng thái phòng
        if (room.getStatus() == RoomStatus.UNDER_MAINTENANCE) {
            throw new PolicyViolationException(String.format("Phòng '%s' đang bảo trì, không thể đặt.", room.getName()));
        }

        // 2. Kiểm tra quyền hạn (Role)
        Set<Role> requiredRoles = room.getRequiredRoles();
        if (requiredRoles != null && !requiredRoles.isEmpty()) {
            boolean hasPermission = organizer.getRoles().stream().anyMatch(requiredRoles::contains);
            if (!hasPermission) {
                throw new PolicyViolationException(String.format("Người tổ chức (%s) không có quyền đặt phòng '%s'",
                        organizer.getFullName(), room.getName()));
            }
        }

        // 3. Kiểm tra Xung đột phòng (Logic Mới: Soft Lock)
        // Chỉ chặn nếu đã có lịch CONFIRMED. Nếu chỉ có lịch PENDING, vẫn cho đặt tiếp.
        boolean hasConfirmedBooking = meetingRepository.existsConfirmedMeetingInTimeRange(
                room.getId(), startTime, endTime, meetingIdToIgnore);

        if (hasConfirmedBooking) {
            throw new MeetingConflictException(String.format(
                "Phòng '%s' đã có lịch CHÍNH THỨC (CONFIRMED) vào khung giờ này. Bạn không thể đặt chồng lên.", 
                room.getName()));
        }

        // 4. Kiểm tra Xung đột người tham gia
        Set<Long> userIdsToCheck = participants.stream().map(User::getId).collect(Collectors.toSet());
        userIdsToCheck.add(organizer.getId());
        List<Meeting> conflictingMeetings = meetingRepository.findConflictingMeetingsForUsers(userIdsToCheck, startTime, endTime, meetingIdToIgnore);
        
        if (!conflictingMeetings.isEmpty()) {
            String conflictingUser = conflictingMeetings.get(0).getParticipants().stream()
                    .map(p -> p.getUser().getFullName()).collect(Collectors.joining(", "));
            throw new MeetingConflictException(String.format("Người tham dự (%s) bị trùng lịch vào lúc %s", conflictingUser, startTime));
        }

        // 5. Kiểm tra Xung đột thiết bị
        Set<Long> deviceIds = devices.stream().map(Device::getId).collect(Collectors.toSet());
        if (meetingRepository.isDeviceBusy(deviceIds, startTime, endTime, meetingIdToIgnore)) {
            throw new MeetingConflictException(String.format("Một hoặc nhiều thiết bị bạn chọn đã bị đặt vào lúc %s", startTime));
        }
    }

    private List<TimeSlotDTO> calculateRecurrenceSlots(LocalDateTime firstStartTime,
            LocalDateTime firstEndTime, RecurrenceRuleDTO rule) {
        List<TimeSlotDTO> slots = new ArrayList<>();
        long durationMinutes = java.time.Duration.between(firstStartTime, firstEndTime).toMinutes();

        if (rule.getFrequency() == FrequencyType.WEEKLY && rule.getDaysOfWeek() != null && !rule.getDaysOfWeek().isEmpty()) {
            LocalDate currentDate = firstStartTime.toLocalDate();
            LocalDate endDate = rule.getRepeatUntil();
            while (!currentDate.isAfter(endDate)) {
                if (rule.getDaysOfWeek().contains(currentDate.getDayOfWeek())) {
                    LocalDateTime slotStart = currentDate.atTime(firstStartTime.toLocalTime());
                    if (!slotStart.isBefore(firstStartTime)) {
                        slots.add(new TimeSlotDTO(slotStart, slotStart.plusMinutes(durationMinutes)));
                    }
                }
                if (currentDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                    int weeksToSkip = rule.getInterval() - 1;
                    if (weeksToSkip > 0) currentDate = currentDate.plusWeeks(weeksToSkip);
                }
                currentDate = currentDate.plusDays(1);
            }
            return slots;
        }

        LocalDateTime currentStartTime = firstStartTime;
        while (!currentStartTime.toLocalDate().isAfter(rule.getRepeatUntil())) {
            slots.add(new TimeSlotDTO(currentStartTime, currentStartTime.plusMinutes(durationMinutes)));
            switch (rule.getFrequency()) {
                case DAILY -> currentStartTime = currentStartTime.plusDays(rule.getInterval());
                case WEEKLY -> currentStartTime = currentStartTime.plusWeeks(rule.getInterval());
                case MONTHLY -> currentStartTime = currentStartTime.plusMonths(rule.getInterval());
                default -> currentStartTime = currentStartTime.plusDays(1);
            }
        }
        return slots;
    }

    private MeetingDTO convertMeetingToDTO(Meeting meeting) {
        if (meeting == null) return null;
        MeetingDTO dto = modelMapper.map(meeting, MeetingDTO.class);
        if (meeting.getParticipants() != null) {
            List<MeetingParticipantDTO> participantDTOs = meeting.getParticipants().stream()
                    .map(this::convertParticipantToDTO).collect(Collectors.toList());
            dto.setParticipants(participantDTOs);
        }
        return dto;
    }

    private MeetingParticipantDTO convertParticipantToDTO(MeetingParticipant participant) {
        if (participant == null) return null;
        MeetingParticipantDTO dto = new MeetingParticipantDTO();
        dto.setStatus(participant.getStatus());
        if (participant.getUser() != null) {
            dto.setId(participant.getUser().getId());
            dto.setFullName(participant.getUser().getFullName());
        }
        return dto;
    }

    private void notifyAdminsForApproval(Meeting meeting, User organizer) {
        List<User> admins = userRepository.findAllAdmins();
        String msgToAdmin = String.format("CẬP NHẬT: Yêu cầu duyệt lại. %s đã thay đổi lịch họp tại phòng '%s'.",
                organizer.getFullName(), meeting.getRoom().getName());
        for (User admin : admins) {
            notificationService.createNotification(admin, msgToAdmin, meeting);
        }
    }

    private void sendNotificationsToParticipants(Meeting meeting, String message) {
        meeting.getParticipants().stream()
                .filter(p -> p.getStatus() == ParticipantStatus.PENDING)
                .forEach(p -> notificationService.createNotification(p.getUser(), message, meeting));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookedSlotDTO> getRoomSchedule(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Meeting> meetings = meetingRepository.findMeetingsByRoomAndTimeRange(roomId, startTime, endTime);
        return meetings.stream().map(m -> new BookedSlotDTO(m.getId(), m.getTitle(), m.getStartTime(), m.getEndTime(), m.getOrganizer().getFullName())).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookedSlotDTO> getDeviceSchedule(Long deviceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Meeting> meetings = meetingRepository.findMeetingsByDeviceAndTimeRange(deviceId, startTime, endTime);
        return meetings.stream().map(m -> new BookedSlotDTO(m.getId(), m.getTitle(), m.getStartTime(), m.getEndTime(), m.getOrganizer().getFullName())).collect(Collectors.toList());
    }

    @Override
    public void checkInByQrCode(String qrCode, Long currentUserId) {
        Meeting meeting = meetingRepository.findByCheckinCode(qrCode)
                .orElseThrow(() -> new EntityNotFoundException("Mã QR không hợp lệ hoặc cuộc họp không tồn tại."));

        Long organizerId = meeting.getOrganizer().getId();
        LocalDateTime now = LocalDateTime.now();
        
        if (now.isBefore(meeting.getStartTime().minusMinutes(30))) {
            throw new PolicyViolationException("Chưa đến giờ điểm danh.");
        }
        if (now.isAfter(meeting.getEndTime())) {
            throw new PolicyViolationException("Cuộc họp đã kết thúc.");
        }

        MeetingParticipant participant = meeting.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(currentUserId))
                .findFirst().orElse(null);

        if (participant == null) {
            if (organizerId.longValue() == currentUserId.longValue()) {
                participant = new MeetingParticipant(meeting.getOrganizer(), ParticipantStatus.ACCEPTED, null);
                participant.setMeeting(meeting);
                meeting.getParticipants().add(participant);
            } else {
                throw new PolicyViolationException("Bạn không có tên trong danh sách tham dự.");
            }
        }

        if (participant.getCheckedInAt() != null) {
            throw new PolicyViolationException("Bạn đã check-in trước đó rồi.");
        }

        participant.setCheckedInAt(now);
        if (organizerId.equals(currentUserId)) {
            meeting.setCheckedIn(true);
        }
        meetingRepository.save(meeting);
    }

    @Override
    @Transactional(readOnly = true)
    public String generateGoogleCalendarLink(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cuộc họp: " + meetingId));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));
        String startUtc = formatter.format(meeting.getStartTime().atZone(ZoneId.systemDefault()).toInstant());
        String endUtc = formatter.format(meeting.getEndTime().atZone(ZoneId.systemDefault()).toInstant());
        String title = encodeValue(meeting.getTitle());
        String details = encodeValue(meeting.getDescription() != null ? meeting.getDescription() : "");
        String location = encodeValue(meeting.getRoom().getName());
        if (meeting.getOrganizer() != null) {
            details += encodeValue("\n\nNgười tổ chức: " + meeting.getOrganizer().getFullName());
        }
        return String.format("https://calendar.google.com/calendar/render?action=TEMPLATE&text=%s&dates=%s/%s&details=%s&location=%s",
                title, startUtc, endUtc, details, location);
    }

    private String encodeValue(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}