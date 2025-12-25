package com.cmc.meeting.infrastructure.persistence.jpa.adapter;

import com.cmc.meeting.domain.model.Device;
import com.cmc.meeting.domain.model.Meeting;
import com.cmc.meeting.domain.model.MeetingParticipant;
import com.cmc.meeting.domain.model.User;
import com.cmc.meeting.domain.port.repository.MeetingRepository;
import com.cmc.meeting.domain.port.repository.UserRepository;
import com.cmc.meeting.infrastructure.persistence.jpa.entity.DeviceEntity;
import com.cmc.meeting.infrastructure.persistence.jpa.entity.MeetingEntity;
import com.cmc.meeting.infrastructure.persistence.jpa.embeddable.EmbeddableParticipant;
import com.cmc.meeting.infrastructure.persistence.jpa.repository.*;
import jakarta.annotation.PostConstruct;

import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class MeetingRepositoryAdapter implements MeetingRepository {

    private final SpringDataMeetingRepository jpaRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;

    public MeetingRepositoryAdapter(SpringDataMeetingRepository jpaRepository,
                                    ModelMapper modelMapper,
                                    UserRepository userRepository) {
        this.jpaRepository = jpaRepository;
        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
    }

    @Override
    public Meeting save(Meeting meeting) {
        MeetingEntity entity = toEntity(meeting);
        if (meeting.getGoogleEventId() != null) {
            entity.setGoogleEventId(meeting.getGoogleEventId());
        }
        MeetingEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Meeting> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean isRoomBusy(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore) {
        return jpaRepository.findRoomOverlap(roomId, startTime, endTime, meetingIdToIgnore);
    }

    @Override
    public boolean existsConfirmedMeetingInTimeRange(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore) {
        // Gọi hàm Query đã viết trong SpringDataMeetingRepository
        return jpaRepository.existsConfirmedMeetingInTimeRange(roomId, startTime, endTime, meetingIdToIgnore);
    }

    @Override
    public List<Meeting> findPendingConflicts(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long excludedMeetingId) {
        // Gọi hàm Query lấy danh sách Entity
        List<MeetingEntity> entities = jpaRepository.findPendingConflicts(roomId, startTime, endTime, excludedMeetingId);
        
        // Map từ Entity sang Domain
        return entities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meeting> findAllByUserId(Long userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meeting> findConfirmedMeetingsInDateRange(LocalDateTime from, LocalDateTime to) {
        return jpaRepository.findConfirmedMeetingsInDateRange(from, to).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // --- CÁC HÀM HELPER (ĐÃ SỬA LỖI MAPPING) ---

    private Meeting toDomain(MeetingEntity entity) {
        if (entity == null) return null;

        // 1. Map các trường đơn giản
        Meeting meeting = modelMapper.map(entity, Meeting.class);

        // 2. Map 'participants' (ĐÃ TỐI ƯU QUERY & SỬA LỖI MAPPING)
        if (entity.getParticipants() != null && !entity.getParticipants().isEmpty()) {
            
            // B1: Lấy danh sách tất cả User ID để query 1 lần
            Set<Long> userIds = entity.getParticipants().stream()
                    .map(EmbeddableParticipant::getUserId)
                    .collect(Collectors.toSet());

            // B2: Query 1 lần (giả sử UserRepository trả về List<User>)
            // Nếu findAllById trả về Iterable, cần ép kiểu hoặc dùng Stream
            List<User> users = userRepository.findAllById(userIds); // Cần đảm bảo UserRepository có hàm này

            // B3: Tạo Map để tra cứu nhanh
            Map<Long, User> userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));

            // B4: Map sang Domain Object
            Set<MeetingParticipant> participants = entity.getParticipants().stream()
                    .map(embeddable -> {
                        User user = userMap.get(embeddable.getUserId());
                        if (user == null) return null; // Skip nếu user không tồn tại
                        
                        MeetingParticipant p = new MeetingParticipant(user, embeddable.getStatus(), embeddable.getResponseToken());
                        
                        // === SỬA LỖI 1: MAP THỜI GIAN CHECK-IN TỪ DB LÊN ===
                        if (embeddable.getCheckedInAt() != null) {
                            p.setCheckedInAt(embeddable.getCheckedInAt());
                        }
                        // ===================================================

                        // Liên kết ngược (nếu cần thiết cho logic save sau này)
                        p.setMeeting(meeting);
                        return p;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            meeting.setParticipants(participants);
        }

        return meeting;
    }

    private MeetingEntity toEntity(Meeting meeting) {
        if (meeting == null) return null;
        MeetingEntity entity = modelMapper.map(meeting, MeetingEntity.class);

        if (meeting.getParticipants() != null) {
            Set<EmbeddableParticipant> embeddableParticipants = meeting.getParticipants().stream()
                    .map(participant -> {
                        EmbeddableParticipant embeddable = new EmbeddableParticipant();
                        embeddable.setUserId(participant.getUser().getId());
                        embeddable.setStatus(participant.getStatus());
                        embeddable.setResponseToken(participant.getResponseToken());
                        
                        // === SỬA LỖI 2: MAP THỜI GIAN CHECK-IN XUỐNG DB ===
                        if (participant.getCheckedInAt() != null) {
                            embeddable.setCheckedInAt(participant.getCheckedInAt());
                        }
                        // ===================================================
                        
                        return embeddable;
                    })
                    .collect(Collectors.toSet());
            entity.setParticipants(embeddableParticipants);
        }
        return entity;
    }

    @Override
    public Optional<Meeting> findMeetingByParticipantToken(String token) {
        return jpaRepository.findMeetingByParticipantToken(token)
                .map(this::toDomain);
    }

    @Override
    public Optional<Meeting> findCheckInEligibleMeeting(Long organizerId, Long roomId, LocalDateTime now) {
        LocalDateTime timeStartWindow = now.minusMinutes(15);
        LocalDateTime timeEndWindow = now.plusMinutes(30);

        return jpaRepository.findCheckInEligibleMeeting(organizerId, roomId, timeStartWindow, timeEndWindow)
                .map(this::toDomain);
    }

    @Override
    public List<Meeting> findUncheckedInMeetings(LocalDateTime cutoffTime) {
        return jpaRepository.findUncheckedInMeetings(cutoffTime).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meeting> findMeetingsForUsersInDateRange(Set<Long> userIds, LocalDateTime from, LocalDateTime to) {
        return jpaRepository.findMeetingsForUsersInDateRange(userIds, from, to).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meeting> findCanceledMeetingsInDateRange(LocalDateTime from, LocalDateTime to) {
        return jpaRepository.findCanceledMeetingsInDateRange(from, to).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @PostConstruct
    public void configureMapper() {
        TypeMap<MeetingEntity, Meeting> entityToDomainMap = modelMapper.typeMap(MeetingEntity.class, Meeting.class);
        // Skip để map thủ công trong toDomain()
        entityToDomainMap.addMappings(mapper -> mapper.skip(Meeting::setParticipants)); 

        TypeMap<Meeting, MeetingEntity> domainToEntityMap = modelMapper.typeMap(Meeting.class, MeetingEntity.class);
        domainToEntityMap.addMappings(mapper -> mapper.skip(MeetingEntity::setParticipants));

        modelMapper.createTypeMap(DeviceEntity.class, Device.class);
        modelMapper.createTypeMap(Device.class, DeviceEntity.class);
    }

    @Override
    public List<Meeting> findAllBySeriesId(String seriesId) {
        return jpaRepository.findAllBySeriesId(seriesId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meeting> findMeetingsWithGuestsInDateRange(LocalDateTime from, LocalDateTime to) {
        return jpaRepository.findMeetingsWithGuestsInDateRange(from, to).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByOrganizerId(Long organizerId) {
        return jpaRepository.existsByOrganizerId(organizerId);
    }

    @Override
    public Page<Meeting> findAllByUserId(Long userId, Pageable pageable) {
        Page<MeetingEntity> page = jpaRepository.findMyMeetings(userId, pageable);
        return page.map(this::toDomain);
    }

    @Override
    public List<Meeting> findConflictingMeetingsForUsers(Set<Long> userIds, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore) {
        List<MeetingEntity> entities = jpaRepository.findConflictingMeetingsForUsers(userIds, startTime, endTime, meetingIdToIgnore);
        return entities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Set<Long> findBookedDevicesInTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return jpaRepository.findBookedDeviceIdsInTimeRange(startTime, endTime);
    }

    @Override
    public Page<Meeting> findAllMeetings(Pageable pageable) {
        Page<MeetingEntity> page = jpaRepository.findAllByOrderByStartTimeDesc(pageable);
        return page.map(this::toDomain);
    }

    @Override
    public boolean isDeviceBusy(Set<Long> deviceIds, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore) {
        if (deviceIds == null || deviceIds.isEmpty()) return false;
        return jpaRepository.existsConflictingDevice(deviceIds, startTime, endTime, meetingIdToIgnore);
    }

    @Override
    public List<Meeting> findFutureMeetingsByOrganizerId(Long organizerId, LocalDateTime now) {
        return jpaRepository.findFutureMeetingsByOrganizerId(organizerId, now).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Meeting> findMeetingsByRoomAndTimeRange(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        return jpaRepository.findMeetingsByRoomAndTimeRange(roomId, startTime, endTime).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Meeting> findMeetingsByDeviceAndTimeRange(Long deviceId, LocalDateTime startTime, LocalDateTime endTime) {
        return jpaRepository.findMeetingsByDeviceAndTimeRange(deviceId, startTime, endTime).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Meeting> findByStartTimeBetween(LocalDateTime start, LocalDateTime end) {
        // Gọi hàm findAllByStartTimeBetween vừa thêm ở Bước 1
        List<MeetingEntity> entities = jpaRepository.findAllByStartTimeBetween(start, end);
        
        return entities.stream()
                .map(this::toDomain) // Sử dụng hàm helper toDomain của bạn
                .collect(Collectors.toList());
    }
    @Override
    public Optional<Meeting> findCurrentMeetingAtRoom(Long roomId, LocalDateTime checkTime) {
        // Gọi hàm findActiveMeetingInRoom vừa thêm ở Bước 1
        return jpaRepository.findActiveMeetingInRoom(roomId, checkTime)
                .map(this::toDomain);
    }

    @Override
    public Optional<Meeting> findByCheckinCode(String checkinCode) {
        return jpaRepository.findByCheckinCode(checkinCode)
                .map(this::toDomain); // Dùng toDomain thay vì ModelMapper thuần
    }
}