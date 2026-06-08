-- 1. Đặt biến mật khẩu băm (cho "123456")
-- (Tôi đã tạo một hash hợp lệ mới cho "123456")
SET @hashed_password = '$2a$10$Pg3x77SOBql4SZ8ZuOhglOfQ//ko.z5HS6U062JEXoQ30dn6MQRD6';

-- 2. Thêm phòng họp TRƯỚC
INSERT INTO rooms (id, name, capacity, location, requires_approval) VALUES (1, 'Phòng Họp Sao Hỏa', 10, 'Tầng 10', FALSE)
    ON DUPLICATE KEY UPDATE name='Phòng Họp Sao Hỏa';

-- 3. Thêm Users
INSERT INTO users (id, username, full_name, password, is_google_linked) VALUES (1, 'user1@cmc.com', 'User Một', @hashed_password, FALSE)
    ON DUPLICATE KEY UPDATE username='user1@cmc.com', password=@hashed_password;

INSERT INTO users (id, username, full_name, password, is_google_linked) VALUES (2, 'user2@cmc.com', 'User Hai', @hashed_password, FALSE)
    ON DUPLICATE KEY UPDATE username='user2@cmc.com', password=@hashed_password;

INSERT INTO app_configuration (config_key, config_value, description) 
VALUES ('auto.cancel.grace.minutes', '15', 'Thời gian (phút) chờ check-in trước khi tự động hủy họp')
    ON DUPLICATE KEY UPDATE config_value='15';


-- BỔ SUNG: (BS-32) Template Email Nội bộ
INSERT INTO app_configuration (config_key, config_value, description) 
VALUES (
    'email.template.internal', 
    '<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <style>
      body {
        font-family: Arial, sans-serif;
        line-height: 1.6;
      }
      .container {
        width: 90%;
        margin: auto;
        padding: 20px;
        border: 1px solid #ddd;
        border-radius: 5px;
      }
      .header {
        font-size: 24px;
        color: #333;
      }
      .content {
        margin-top: 20px;
      }
      .footer {
        margin-top: 30px;
        font-size: 12px;
        color: #888;
      }
      .button {
        background-color: #007bff;
        color: white;
        padding: 10px 15px;
        text-decoration: none;
        border-radius: 5px;
      }
      .button-decline {
        background-color: #dc3545;
        margin-left: 10px;
      }
    </style>
  </head>
  <body>
    <div class="container">
      <div class="header">Thư mời tham gia cuộc họp</div>
      <div class="content">
        <p>Chào bạn,</p>
        <p>
          Bạn đã được mời tham gia cuộc họp:
          <strong th:text="${title}">[Tên cuộc họp]</strong>
        </p>

        <p><strong>Chi tiết:</strong></p>
        <ul>
          <li>
            <strong>Thời gian:</strong>
            <span th:text="${startTime}">[Giờ bắt đầu]</span> -
            <span th:text="${endTime}">[Giờ kết thúc]</span>
          </li>
          <li>
            <strong>Phòng họp:</strong>
            <span th:text="${roomName}">[Tên phòng]</span>
          </li>
          <li>
            <strong>Người tổ chức:</strong>
            <span th:text="${organizerName}">[Người tổ chức]</span>
          </li>
        </ul>

        <p style="text-align: center; margin-top: 25px">
          <a th:href="${acceptUrl}" class="button"> Chấp nhận </a>
          <a th:href="${declineUrl}" class="button button-decline"> Từ chối </a>
        </p>
      </div>
      <div class="footer">
        <p>Vui lòng tham gia đúng giờ.</p>
      </div>
    </div>
  </body>
</html>
', 
    'Template email cho nhân viên (có nút Accept/Decline)'
) ON DUPLICATE KEY UPDATE config_value='<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <style>
      body {
        font-family: Arial, sans-serif;
        line-height: 1.6;
      }
      .container {
        width: 90%;
        margin: auto;
        padding: 20px;
        border: 1px solid #ddd;
        border-radius: 5px;
      }
      .header {
        font-size: 24px;
        color: #333;
      }
      .content {
        margin-top: 20px;
      }
      .footer {
        margin-top: 30px;
        font-size: 12px;
        color: #888;
      }
      .button {
        background-color: #007bff;
        color: white;
        padding: 10px 15px;
        text-decoration: none;
        border-radius: 5px;
      }
      .button-decline {
        background-color: #dc3545;
        margin-left: 10px;
      }
    </style>
  </head>
  <body>
    <div class="container">
      <div class="header">Thư mời tham gia cuộc họp</div>
      <div class="content">
        <p>Chào bạn,</p>
        <p>
          Bạn đã được mời tham gia cuộc họp:
          <strong th:text="${title}">[Tên cuộc họp]</strong>
        </p>

        <p><strong>Chi tiết:</strong></p>
        <ul>
          <li>
            <strong>Thời gian:</strong>
            <span th:text="${startTime}">[Giờ bắt đầu]</span> -
            <span th:text="${endTime}">[Giờ kết thúc]</span>
          </li>
          <li>
            <strong>Phòng họp:</strong>
            <span th:text="${roomName}">[Tên phòng]</span>
          </li>
          <li>
            <strong>Người tổ chức:</strong>
            <span th:text="${organizerName}">[Người tổ chức]</span>
          </li>
        </ul>

        <p style="text-align: center; margin-top: 25px">
          <a th:href="${acceptUrl}" class="button"> Chấp nhận </a>
          <a th:href="${declineUrl}" class="button button-decline"> Từ chối </a>
        </p>
      </div>
      <div class="footer">
        <p>Vui lòng tham gia đúng giờ.</p>
      </div>
    </div>
  </body>
</html>';

-- BỔ SUNG: (BS-32) Template Email Khách
INSERT INTO app_configuration (config_key, config_value, description) 
VALUES (
    'email.template.guest', 
    '<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; }
        .container { width: 90%; margin: auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
        .header { font-size: 24px; color: #333; }
        .content { margin-top: 20px; }
        .location-box { background-color: #f9f9f9; border: 1px solid #eee; padding: 15px; margin-top: 20px; }
        .footer { margin-top: 30px; font-size: 12px; color: #888; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">Thư mời tham gia cuộc họp</div>
        <div class="content">
            <p>Kính gửi Quý khách,</p>
            <p>Bạn đã được mời tham gia cuộc họp với chủ đề: <strong th:text="${title}">[Tên cuộc họp]</strong></p>

            <p><strong>Chi tiết:</strong></p>
            <ul>
                <li><strong>Thời gian:</strong> <span th:text="${startTime}">[Giờ bắt đầu]</span> - <span th:text="${endTime}">[Giờ kết thúc]</span></li>
                <li><strong>Phòng họp:</strong> <span th:text="${roomName}">[Tên phòng]</span></li>
                <li><strong>Người tổ chức:</strong> <span th:text="${organizerName}">[Người tổ chức]</span></li>
            </ul>

            <div class="location-box">
                <strong>Thông tin địa điểm:</strong>
                <p>
                    Tòa nhà CMC, 11 Duy Tân, Cầu Giấy, Hà Nội <br/>
                    Người liên hệ (Lễ tân): 0123.456.789 <br/>
                    </p>
            </div>
        </div>
        <div class="footer">
            <p>Vui lòng xác nhận tham dự bằng cách trả lời email này. Trân trọng cảm ơn.</p>
        </div>
    </div>
</body>
</html>', 
    'Template email cho khách (có địa chỉ)'
) ON DUPLICATE KEY UPDATE config_value='<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; }
        .container { width: 90%; margin: auto; padding: 20px; border: 1px solid #ddd; border-radius: 5px; }
        .header { font-size: 24px; color: #333; }
        .content { margin-top: 20px; }
        .location-box { background-color: #f9f9f9; border: 1px solid #eee; padding: 15px; margin-top: 20px; }
        .footer { margin-top: 30px; font-size: 12px; color: #888; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">Thư mời tham gia cuộc họp</div>
        <div class="content">
            <p>Kính gửi Quý khách,</p>
            <p>Bạn đã được mời tham gia cuộc họp với chủ đề: <strong th:text="${title}">[Tên cuộc họp]</strong></p>

            <p><strong>Chi tiết:</strong></p>
            <ul>
                <li><strong>Thời gian:</strong> <span th:text="${startTime}">[Giờ bắt đầu]</span> - <span th:text="${endTime}">[Giờ kết thúc]</span></li>
                <li><strong>Phòng họp:</strong> <span th:text="${roomName}">[Tên phòng]</span></li>
                <li><strong>Người tổ chức:</strong> <span th:text="${organizerName}">[Người tổ chức]</span></li>
            </ul>

            <div class="location-box">
                <strong>Thông tin địa điểm:</strong>
                <p>
                    Tòa nhà CMC, 11 Duy Tân, Cầu Giấy, Hà Nội <br/>
                    Người liên hệ (Lễ tân): 0123.456.789 <br/>
                    </p>
            </div>
        </div>
        <div class="footer">
            <p>Vui lòng xác nhận tham dự bằng cách trả lời email này. Trân trọng cảm ơn.</p>
        </div>
    </div>
</body>
</html>';

