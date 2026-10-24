# DevDex

<p align="center"><img src="https://raw.githubusercontent.com/devboilol/devdex/main/app/src/main/res/drawable/devdex_icon.webp" width="100" alt="DevDex logo"/></p>

<h1 align="center">DevDex</h1>

<p align="center">A Windows-inspired desktop experience for Android</p>

<p align="center">
  <a href="https://github.com/devboilol/devdex/actions/workflows/build-apk.yml"><img src="https://img.shields.io/badge/Build-GitHub%20Actions-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white" alt="Build workflow"/></a>
  <a href="https://github.com/devboilol/devdex/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="GPL-3.0 license"/></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?style=for-the-badge&logo=android" alt="Android 8.0+"/>
</p>

<p align="center"><a href="#english">English</a> · <a href="#tiếng-việt">Tiếng Việt</a></p>

---

<a id="english"></a>
## English

### About

**DevDex** is an independent continuation of the Android desktop experience built on the work of **YoukiDex** and **Smart Dock**. It brings a taskbar, app dock, movable/resizable app windows, and desktop-style controls to Android. DevDex focuses on a desktop-first setup, window-management improvements, localization, and portable settings backups.

### Highlights

- Desktop-style taskbar and app dock.
- Movable and resizable app windows, with maximize/restore and close controls for windowed apps.
- Fullscreen activities are intended to remain free of DevDex window-control overlays.
- First-run language selection: System default, English, or Vietnamese.
- Settings export/import using typed JSON backups, with support for older `.sdp` backups.
- Some advanced system operations require Root or Shizuku; core desktop UI features generally do not.

### Quick start

1. Install the APK and open DevDex.
2. Complete the first-run setup and choose a language.
3. Review each permission carefully. Accessibility and overlay permissions are essential to the desktop experience.
4. Use DevDex as your launcher or start it on demand using the available shortcut or Quick Settings tile.

### Settings backup

Open **Settings → Advanced → Backup/Restore Preferences** to export or import settings. New backups use JSON format; older `.sdp` backups remain supported.

### Safety notice

> **Recommended: use DevDex on a secondary device, preferably a tablet.** Accessibility and overlay features may conflict with banking, government, identity-verification, payment, enterprise-authentication, or other security-sensitive apps. Some apps may block access while overlays or desktop controls are active. Keep a regular launcher available and do not depend on DevDex for critical secure workflows on your primary device.

### Compatibility and limitations

- Designed for Android 8.0 and newer; actual behavior depends on the device and Android version.
- Selected system-level features and task/window operations may require Root or Shizuku.
- Some manufacturers disable freeform windows by default.
- Window controls and fullscreen detection depend partly on Android and device-manufacturer behavior. Please test on your own device.
- Always-on-top pinning for arbitrary apps is not implemented through the ordinary public Android APIs.

### Credits and acknowledgements

DevDex stands on the work of open-source developers. **Thank you to the original authors and contributors:**

- **[Smart Dock](https://github.com/axel358/smartdock) — Axel358:** thank you for creating the original desktop-mode launcher and its open-source foundation.
- **[YoukiDex](https://github.com/mrYouki/YoukiDex-Android-Desktop) — mrYouki:** thank you for the extensive development and improvements that carried Smart Dock forward and formed the direct basis for DevDex.
- Thank you to the wider Android and open-source communities whose tools, libraries, bug reports, and contributions help make projects like this possible.

DevDex is an independent continuation and is **not an official release** of Smart Dock or YoukiDex, nor is it affiliated with or endorsed by their original maintainers. Original copyright notices and applicable license terms are preserved. See [LICENSE](LICENSE).

---

<a id="tiếng-việt"></a>
## Tiếng Việt

### Giới thiệu

**DevDex** là dự án tiếp nối độc lập từ nền tảng Android desktop được phát triển qua **YoukiDex** và **Smart Dock**. Ứng dụng mang đến thanh tác vụ, dock ứng dụng, cửa sổ có thể di chuyển/thay đổi kích thước và các điều khiển kiểu desktop trên Android. DevDex tập trung cải thiện trải nghiệm khởi động vào desktop, quản lý cửa sổ, đa ngôn ngữ và sao lưu cài đặt.

### Tính năng chính

- Thanh tác vụ và dock ứng dụng theo phong cách desktop.
- Cửa sổ ứng dụng có thể di chuyển và thay đổi kích thước; có nút phóng to/khôi phục và đóng đối với ứng dụng chạy dạng cửa sổ.
- Mục tiêu là không hiển thị lớp điều khiển cửa sổ DevDex trên các hoạt động chạy toàn màn hình.
- Chọn ngôn ngữ ở lần khởi chạy đầu tiên: theo hệ thống, tiếng Anh hoặc tiếng Việt.
- Xuất/nhập cài đặt bằng bản sao lưu JSON có lưu kiểu dữ liệu; vẫn hỗ trợ bản sao lưu `.sdp` cũ.
- Một số thao tác hệ thống nâng cao cần Root hoặc Shizuku; các tính năng giao diện desktop cơ bản thường không cần.

### Bắt đầu sử dụng

1. Cài APK và mở DevDex.
2. Hoàn tất phần thiết lập ban đầu và chọn ngôn ngữ.
3. Đọc kỹ từng quyền trước khi cấp. Quyền Trợ năng và quyền hiển thị trên ứng dụng khác là thành phần quan trọng của trải nghiệm desktop.
4. Dùng DevDex làm trình khởi chạy mặc định hoặc mở khi cần bằng lối tắt hay ô Cài đặt nhanh nếu thiết bị hỗ trợ.

### Sao lưu cài đặt

Mở **Cài đặt → Advanced → Backup/Restore Preferences** để xuất hoặc nhập cài đặt. Bản sao lưu mới dùng định dạng JSON; các bản `.sdp` cũ vẫn được hỗ trợ.

### Cảnh báo an toàn

> **Khuyến nghị dùng DevDex trên thiết bị phụ, tốt nhất là máy tính bảng.** Các tính năng Trợ năng và hiển thị đè có thể xung đột với ứng dụng ngân hàng, dịch vụ chính phủ, xác minh danh tính, thanh toán, xác thực doanh nghiệp hoặc ứng dụng nhạy cảm về bảo mật. Một số ứng dụng có thể chặn truy cập khi lớp phủ hoặc nút điều khiển cửa sổ đang hoạt động. Hãy giữ lại trình khởi chạy thông thường và không phụ thuộc vào DevDex cho các thao tác bảo mật quan trọng trên thiết bị chính.

### Tương thích và giới hạn

- Hướng tới Android 8.0 trở lên; hành vi thực tế phụ thuộc thiết bị và phiên bản Android.
- Một số tính năng hệ thống hoặc thao tác quản lý tác vụ/cửa sổ có thể cần Root hoặc Shizuku.
- Một số nhà sản xuất tắt sẵn chế độ cửa sổ tự do (freeform windows).
- Việc nhận diện toàn màn hình và hiển thị điều khiển cửa sổ còn phụ thuộc Android và tùy biến của nhà sản xuất. Hãy kiểm tra trên thiết bị thực tế.
- Tính năng ghim cửa sổ ứng dụng bất kỳ luôn nổi trên cùng chưa được triển khai bằng các API Android công khai thông thường.

### Ghi công và lời cảm ơn

DevDex được xây dựng dựa trên công sức của các nhà phát triển mã nguồn mở. **Xin chân thành cảm ơn các tác giả và cộng tác viên ban đầu:**

- **[Smart Dock](https://github.com/axel358/smartdock) — Axel358:** cảm ơn tác giả đã tạo ra trình khởi chạy desktop gốc và nền tảng mã nguồn mở ban đầu.
- **[YoukiDex](https://github.com/mrYouki/YoukiDex-Android-Desktop) — mrYouki:** cảm ơn tác giả đã tiếp tục phát triển, cải tiến Smart Dock và tạo nên nền tảng trực tiếp để DevDex kế thừa.
- Cảm ơn cộng đồng Android và mã nguồn mở vì các công cụ, thư viện, báo lỗi và đóng góp đã giúp những dự án như thế này có thể phát triển.

DevDex là dự án tiếp nối độc lập, **không phải bản phát hành chính thức** của Smart Dock hoặc YoukiDex, cũng không liên kết hay được các tác giả gốc bảo trợ. Các thông báo bản quyền và điều khoản giấy phép áp dụng được giữ lại. Xem [LICENSE](LICENSE).

---

## License / Giấy phép

This project is distributed under **GNU GPL-3.0**. / Dự án được phân phối theo giấy phép **GNU GPL-3.0**.

See [LICENSE](LICENSE) for the full terms. / Xem [LICENSE](LICENSE) để đọc đầy đủ điều khoản.

## Support / Báo lỗi

For bug reports and feature requests, open a [GitHub Issue](https://github.com/devboilol/devdex/issues). / Để báo lỗi hoặc đề xuất tính năng, hãy tạo [GitHub Issue](https://github.com/devboilol/devdex/issues).
