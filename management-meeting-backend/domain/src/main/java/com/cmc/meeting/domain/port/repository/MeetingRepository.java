package com.cmc.meeting.domain.port.repository;

import com.cmc.meeting.domain.model.Meeting;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Đây là "Hợp đồng" (Contract)
// Nó không biết gì về JPA hay MyBatiS
public interface MeetingRepository {

    Meeting save(Meeting meeting);

    Optional<Meeting> findById(Long id);

    // Sẽ cần thêm các method phức tạp sau
    // ví dụ: boolean isRoomBusy(Long roomId, LocalDateTime start, LocalDateTime
    // end);
    boolean isRoomBusy(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore);

    List<Meeting> findAllByUserId(Long userId);

    List<Meeting> findConfirmedMeetingsInDateRange(LocalDateTime from, LocalDateTime to);

    Optional<Meeting> findMeetingByParticipantToken(String token);

    Optional<Meeting> findCheckInEligibleMeeting(Long organizerId, Long roomId, LocalDateTime now);

    List<Meeting> findUncheckedInMeetings(LocalDateTime cutoffTime);

    List<Meeting> findMeetingsForUsersInDateRange(Set<Long> userIds, LocalDateTime from, LocalDateTime to);

    List<Meeting> findCanceledMeetingsInDateRange(LocalDateTime from, LocalDateTime to);

    List<Meeting> findAllBySeriesId(String seriesId);

    List<Meeting> findMeetingsWithGuestsInDateRange(LocalDateTime from, LocalDateTime to);

    boolean existsByOrganizerId(Long organizerId);

    Page<Meeting> findAllByUserId(Long userId, Pageable pageable);

    List<Meeting> findConflictingMeetingsForUsers(Set<Long> userIds, LocalDateTime startTime, LocalDateTime endTime,
            Long meetingIdToIgnore);

    Set<Long> findBookedDevicesInTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    Page<Meeting> findAllMeetings(Pageable pageable);

    boolean isDeviceBusy(Set<Long> deviceIds, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore);

    List<Meeting> findFutureMeetingsByOrganizerId(Long organizerId, LocalDateTime now);

    List<Meeting> findMeetingsByRoomAndTimeRange(Long roomId, LocalDateTime startTime, LocalDateTime endTime);
    List<Meeting> findMeetingsByDeviceAndTimeRange(Long deviceId, LocalDateTime startTime, LocalDateTime endTime);
    List<Meeting> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    Optional<Meeting> findCurrentMeetingAtRoom(Long roomId, LocalDateTime checkTime);
    Optional<Meeting> findByCheckinCode(String checkinCode);

    boolean existsConfirmedMeetingInTimeRange(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long meetingIdToIgnore);

    List<Meeting> findPendingConflicts(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long excludedMeetingId);
}