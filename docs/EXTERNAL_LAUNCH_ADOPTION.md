# External launch adoption / Tiếp nhận ứng dụng mở từ launcher bên ngoài

## English

### Purpose

DevDex normally supplies freeform launch options only when an app is launched through DevDex. This opt-in experiment tries to adopt an eligible third-party task after it becomes the foreground app, including apps started from the device's stock launcher.

Accessibility events do **not** identify which launcher started an app. The current prototype uses a Home-launcher → eligible-app transition as a heuristic, so it can still miss events on some ROMs. It first re-requests the app's main launcher activity with DevDex's freeform ActivityOptions, then attempts a task resize through available shell/Shizuku/Root routes. It is intentionally disabled by default.

### Enable and test

1. Make sure DevDex's dock/accessibility service is running.
2. Confirm freeform windows work on the ROM, and configure Shizuku/Root if your device requires privileged task operations.
3. Open **Settings → Advanced → Multitasking compatibility**.
4. Enable **Auto-adopt external app windows (experimental)**.
5. Tap a non-system app icon from the stock Home launcher and wait about a second. DevDex first requests the main activity with freeform launch options, then tries to resize the task to about 50% of the usable display area.
6. Check maximize/restore, resize, close, and switching back to the stock launcher. Turn the option off to stop future attempts.

### Safety and known limitations

- This is best-effort and depends on HyperOS/Android honoring freeform launch options or allowing an existing task to change windowing mode.
- The fallback re-requests the app's main launcher activity. Most launchers use this to bring an app task forward, but some apps may return to their main screen or behave differently; test with ordinary apps and save important work first.
- Shell, Shizuku, or Root access may be required. Having Shizuku installed is not the same as granting DevDex permission.
- It only acts on a likely Home-launcher → app transition, but unusual ROM event sequences can still be missed or misclassified.
- A task that is not visible to available task APIs may not be found. The conversion can also be refused by the ROM or an app's orientation/windowing policy.
- System UI, Settings, package installers, permission controllers, and the active Home launcher are excluded.
- The current prototype attempts adoption on a per-task basis and does not guarantee multi-instance task selection when a ROM hides task IDs.
- Do not use this feature to automate banking, payment, government, identity verification, or other security-sensitive apps. Test with ordinary apps on a secondary device first.

This feature is an experiment, not a guarantee that every app can become a floating window. The device must allow freeform windowing for the conversion to work.

## Tiếng Việt

### Mục đích

Thông thường, DevDex chỉ truyền tùy chọn mở cửa sổ tự do khi chính DevDex khởi chạy ứng dụng. Tính năng thử nghiệm này cố gắng tiếp nhận task của một ứng dụng bên thứ ba sau khi nó trở thành ứng dụng phía trước, kể cả khi người dùng mở app từ launcher gốc của thiết bị.

Sự kiện Accessibility **không cho biết launcher nào đã khởi chạy ứng dụng**. Prototype hiện dùng chuyển tiếp từ Home launcher sang ứng dụng đủ điều kiện làm dấu hiệu phỏng đoán, nên vẫn có thể bỏ lỡ sự kiện trên một số ROM. Nó thử yêu cầu mở lại activity chính của ứng dụng bằng ActivityOptions freeform của DevDex, sau đó thử resize task qua shell/Shizuku/Root. Tính năng mặc định tắt.

### Bật và kiểm thử

1. Đảm bảo dock/dịch vụ Trợ năng của DevDex đang chạy.
2. Xác nhận ROM hỗ trợ freeform; cấu hình Shizuku/Root nếu thiết bị cần quyền hệ thống để thao tác task.
3. Mở **Cài đặt → Advanced → Tương thích đa nhiệm**.
4. Bật **Tự tiếp nhận cửa sổ ứng dụng bên ngoài (thử nghiệm)**.
5. Chạm biểu tượng một ứng dụng thông thường từ Home launcher và chờ khoảng một giây. DevDex sẽ yêu cầu mở activity chính bằng tùy chọn freeform, sau đó thử resize task thành cửa sổ chiếm khoảng 50% vùng hiển thị có thể sử dụng.
6. Kiểm tra phóng to/khôi phục, thay đổi kích thước, đóng cửa sổ và chuyển lại launcher gốc. Tắt tùy chọn để ngừng các lần thử tiếp theo.

### An toàn và giới hạn đã biết

- Đây là cơ chế best-effort; kết quả phụ thuộc việc HyperOS/Android có chấp nhận ActivityOptions freeform hoặc cho phép đổi chế độ cửa sổ của task đang chạy hay không.
- Nhánh dự phòng yêu cầu mở activity chính của ứng dụng một lần nữa. Phần lớn launcher dùng cách này để đưa task ứng dụng lên trước, nhưng một số app có thể quay về màn hình chính hoặc hoạt động khác dự kiến; hãy lưu công việc trước khi thử.
- Có thể cần quyền shell, Shizuku hoặc Root. Chỉ cài Shizuku chưa có nghĩa là DevDex đã được cấp quyền.
- Tính năng chỉ can thiệp khi phát hiện chuyển tiếp có vẻ là Home launcher → ứng dụng; chuỗi sự kiện khác thường trên một số ROM vẫn có thể bị bỏ lỡ hoặc nhận diện sai.
- Nếu các API task hiện có không thấy được task, DevDex có thể không tìm ra ID. ROM hoặc chính sách hướng màn hình/chế độ cửa sổ của ứng dụng cũng có thể từ chối chuyển đổi.
- Giao diện hệ thống, Cài đặt, trình cài package, bộ điều khiển quyền và launcher Home đang hoạt động được loại trừ.
- Prototype hiện xử lý theo task và không bảo đảm chọn đúng instance khi ROM che giấu task ID.
- Không dùng tính năng này để tự động thay đổi cách hiển thị ứng dụng ngân hàng, thanh toán, dịch vụ chính phủ, xác minh danh tính hay ứng dụng nhạy cảm về bảo mật. Hãy thử với ứng dụng thông thường trên thiết bị phụ trước.

Đây là tính năng thử nghiệm, không bảo đảm mọi app đều trở thành cửa sổ nổi. ROM phải thực sự cho phép freeform thì việc chuyển đổi mới hoạt động.
