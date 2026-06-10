package com.cmc.meeting.infrastructure.persistence.jpa.repository;

import com.cmc.meeting.infrastructure.persistence.jpa.entity.MeetingEntity;
// (Import UserEntity nếu bạn có)
// import com.cmc.meeting.infrastructure.persistence.jpa.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SpringDataMeetingRepository extends JpaRepository<MeetingEntity, Long> {

        // (US-6) Lấy lịch họp của tôi (phân trang)
        @Query("SELECT m FROM MeetingEntity m " +
                        "LEFT JOIN m.participants p " +
                        // Giả sử EmbeddableParticipant của bạn có trường 'userId'
                        "WHERE m.organizer.id = :userId OR p.userId = :userId " +
                        "ORDER BY m.startTime DESC")
        Page<MeetingEntity> findMyMeetings(@Param("userId") Long userId, Pageable pageable);

        // (US-5) Kiểm tra xung đột người tham gia
        @Query("SELECT m FROM MeetingEntity m JOIN m.participants p " +
                        "WHERE p.userId IN :userIds " +
                        "AND m.status NOT IN ('CANCELLED', 'REJECTED') " +
                        "AND m.startTime < :endTime AND m.endTime > :startTime " +
                        "AND (:ignoreId IS NULL OR m.id != :ignoreId)")
        List<MeetingEntity> findConflictingMeetingsForUsers(
                        @Param("userIds") Set<Long> userIds,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime,
                        @Param("ignoreId") Long ignoreId);

        /**
         * Kiểm tra xung đột phòng (isRoomBusy)
         */
        @Query("SELECT COUNT(m) > 0 FROM MeetingEntity m " +
                        "WHERE m.room.id = :roomId " +
                        "AND m.status NOT IN ('CANCELLED', 'REJECTED') " + // Đổi từ 'CONFIRMED' sang '!= CANCELED'
                        "AND m.startTime < :endTime " +
                        "AND m.endTime > :startTime " + // SỬA LỖI 2: Thêm dấu cách ở cuối
                        "AND (:ignoreId IS NULL OR m.id != :ignoreId)")
        boolean findRoomOverlap(@Param("roomId") Long roomId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime,
                        @Param("ignoreId") Long ignoreId);

        // (US-6) Lấy lịch họp của tôi (bản cũ, không phân trang)
        @Query("SELECT DISTINCT m FROM MeetingEntity m " +
                        "LEFT JOIN m.participants p " +
                        "WHERE m.organizer.id = :userId OR p.userId = :userId")
        List<MeetingEntity> findAllByUserId(@Param("userId") Long userId);

        // (US-22) Báo cáo sử dụng phòng
        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.status = 'CONFIRMED' " +
                        "AND m.startTime >= :from AND m.endTime <= :to")
        List<MeetingEntity> findConfirmedMeetingsInDateRange(
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        // Tìm họp bằng token (để phản hồi)
        @Query("SELECT m FROM MeetingEntity m JOIN m.participants p WHERE p.responseToken = :token")
        Optional<MeetingEntity> findMeetingByParticipantToken(@Param("token") String token);

        // Tìm họp để check-in
        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.organizer.id = :organizerId " +
                        "AND m.room.id = :roomId " +
                        "AND m.status = 'CONFIRMED' " +
                        "AND m.isCheckedIn = false " +
                        "AND m.startTime BETWEEN :timeStartWindow AND :timeEndWindow")
        Optional<MeetingEntity> findCheckInEligibleMeeting(
                        @Param("organizerId") Long organizerId,
                        @Param("roomId") Long roomId,
                        @Param("timeStartWindow") LocalDateTime timeStartWindow,
                        @Param("timeEndWindow") LocalDateTime timeEndWindow);

        // Tìm họp chưa check-in (để tự động hủy)
        @EntityGraph(attributePaths = { "room", "organizer" }) // Chỉ cần organizer để gửi mail hủy
        @Query("SELECT m FROM MeetingEntity m WHERE m.status = 'CONFIRMED' AND m.isCheckedIn = false AND m.startTime <= :cutoffTime")
        List<MeetingEntity> findUncheckedInMeetings(@Param("cutoffTime") LocalDateTime cutoffTime);

        // (US-5) Tìm lịch sử họp của nhóm người
        @Query("SELECT m FROM MeetingEntity m JOIN m.participants p " +
                        "WHERE m.status = 'CONFIRMED' " +
                        "AND m.startTime < :to AND m.endTime > :from " +
                        "AND p.userId IN :userIds") // Giả sử là p.userId
        List<MeetingEntity> findMeetingsForUsersInDateRange(
                        @Param("userIds") Set<Long> userIds,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        // (US-23) Báo cáo hủy họp
        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.status = 'CANCELLED' " +
                        "AND m.cancelledAt >= :from AND m.cancelledAt <= :to")
        List<MeetingEntity> findCanceledMeetingsInDateRange(
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        // Tìm các cuộc họp trong chuỗi
        List<MeetingEntity> findAllBySeriesId(String seriesId);

        // (BS-31) Báo cáo khách
        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.status = 'CONFIRMED' " +
                        "AND m.startTime >= :from AND m.startTime <= :to " +
                        "AND m.guestEmails IS NOT EMPTY")
        List<MeetingEntity> findMeetingsWithGuestsInDateRange(
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

        boolean existsByOrganizerId(Long organizerId);

        // Tìm các thiết bị đã bị đặt
        @Query("SELECT d.id FROM MeetingEntity m JOIN m.devices d " +
                        "WHERE m.status NOT IN ('CANCELLED', 'REJECTED') " +
                        "AND m.startTime < :endTime " +
                        "AND m.endTime > :startTime")
        Set<Long> findBookedDeviceIdsInTimeRange(
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        // (API Admin) Lấy tất cả (phân trang)
        Page<MeetingEntity> findAllByOrderByStartTimeDesc(Pageable pageable);

        // Kiểm tra xung đột thiết bị
        @Query("SELECT COUNT(m) > 0 FROM MeetingEntity m JOIN m.devices d " +
                        "WHERE d.id IN :deviceIds " +
                        "AND m.status NOT IN ('CANCELLED', 'REJECTED') " +
                        "AND m.startTime < :endTime AND m.endTime > :startTime " +
                        "AND (:ignoreId IS NULL OR m.id != :ignoreId)") // SỬA LỖI 2: Thêm dấu cách
        boolean existsConflictingDevice(
                        @Param("deviceIds") Set<Long> deviceIds,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime,
                        @Param("ignoreId") Long ignoreId);

        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.organizer.id = :organizerId " +
                        "AND m.status NOT IN ('CANCELLED', 'REJECTED') " +
                        "AND m.startTime > :now")
        List<MeetingEntity> findFutureMeetingsByOrganizerId(
                        @Param("organizerId") Long organizerId,
                        @Param("now") LocalDateTime now);

        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.room.id = :roomId " +
                        "AND m.status NOT IN ('CANCELLED', 'REJECTED') " +
                        "AND m.startTime < :endTime " + // Chồng lấn (Overlap)
                        "AND m.endTime > :startTime") // Chồng lấn (Overlap)
        List<MeetingEntity> findMeetingsByRoomAndTimeRange(
                        @Param("roomId") Long roomId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        @Query("SELECT m FROM MeetingEntity m JOIN m.devices d " + // JOIN m.devices d
                        "WHERE d.id = :deviceId " + // WHERE d.id
                        "AND m.status NOT IN ('CANCELLED', 'REJECTED') " +
                        "AND m.startTime < :endTime " +
                        "AND m.endTime > :startTime")
        List<MeetingEntity> findMeetingsByDeviceAndTimeRange(
                        @Param("deviceId") Long deviceId,
                        @Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        @EntityGraph(attributePaths = { "room", "participants", "devices", "guestEmails" })
        @Query("SELECT m FROM MeetingEntity m WHERE m.status = 'CONFIRMED' AND m.startTime BETWEEN :start AND :end")
        List<MeetingEntity> findAllByStartTimeBetween(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @EntityGraph(attributePaths = { "room", "participants" })
        @Query("SELECT m FROM MeetingEntity m WHERE m.room.id = :roomId AND :checkTime BETWEEN m.startTime AND m.endTime AND m.status = 'CONFIRMED'")
        Optional<MeetingEntity> findActiveMeetingInRoom(@Param("roomId") Long roomId,
                        @Param("checkTime") LocalDateTime checkTime);

        Optional<MeetingEntity> findByCheckinCode(String checkinCode);

        // 1. Kiểm tra xem có lịch nào ĐÃ CHỐT (CONFIRMED) trong khung giờ này không?
        @Query("SELECT COUNT(m) > 0 FROM MeetingEntity m " +
                        "WHERE m.room.id = :roomId " +
                        "AND m.status = 'CONFIRMED' " + // Chỉ chặn nếu đã CONFIRMED
                        "AND m.id <> :meetingIdToIgnore " +
                        "AND ((m.startTime < :endTime) AND (m.endTime > :startTime))")
        boolean existsConfirmedMeetingInTimeRange(Long roomId, LocalDateTime startTime, LocalDateTime endTime,
                        Long meetingIdToIgnore);

        // 2. Tìm các lịch đang CHỜ DUYỆT (PENDING) bị trùng với khung giờ này (để hủy
        // đi)
        @Query("SELECT m FROM MeetingEntity m " +
                        "WHERE m.room.id = :roomId " +
                        "AND m.status = 'PENDING_APPROVAL' " + // Chỉ tìm những thằng đang chờ
                        "AND m.id <> :excludedMeetingId " + // Trừ chính thằng đang được duyệt ra
                        "AND ((m.startTime < :endTime) AND (m.endTime > :startTime))")
        List<MeetingEntity> findPendingConflicts(Long roomId, LocalDateTime startTime, LocalDateTime endTime,
                        Long excludedMeetingId);
}