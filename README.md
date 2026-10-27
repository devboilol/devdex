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

### Release status
DevDex is preparing a Release Candidate (RC). CI APKs are test artifacts; a downloadable GitHub pre-release is published only from a version tag when stable signing is configured. See the [Release Candidate flow](docs/RELEASE_CANDIDATE_FLOW.md) for the go/no-go checklist.

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

### Detailed installation guide

> **Preview warning:** This is an experimental Community Preview, not a stable release. The APK uses debug signing, so it may not install as an update over another DevDex build. Back up important data first and preferably test on a secondary device. Do not rely on this preview for banking, payments, identity verification, government services, or other sensitive workflows.

#### 1. Download the APK

1. Open the [DevDex 3.5.186 Community Preview release](https://github.com/devboilol/devdex/releases/tag/v3.5.186-preview.1).
2. Under **Assets**, download **`DevDex.3.5.186.Community.Preview.apk`**.
3. Do **not** download either **Source code** archive for installation. Source-code ZIP/TAR files are not APK installers.

#### 2. Allow installation from the app you used to download it

1. Open the downloaded APK from your browser's downloads list or from a file manager.
2. If Android blocks the installation, follow the prompt to open Settings and enable **Allow from this source** / **Install unknown apps** for that browser or file manager.
3. Return to the APK and continue. The exact wording and screen layout vary by Android version and device manufacturer. You can turn this permission off again after installation.

Only install APKs from sources you trust. This release is an early test build.

#### 3. Install and open DevDex

1. Tap **Install** and wait for Android to finish.
2. Tap **Open**.
3. Complete the first-run setup and choose **System default**, **English**, or **Vietnamese**.

#### 4. Review the requested permissions

- **Display over other apps / Appear on top:** enable this for DevDex when prompted if you want its desktop overlays, taskbar, and window controls to work.
- **Accessibility:** if DevDex asks you to enable its Accessibility service, read the Android system explanation, then enable it only if you want the related desktop interactions. You can review or revoke it later in Android Settings → Accessibility.
- Grant permissions only to DevDex and only when needed. Android's permission names and locations can vary by manufacturer.

Some advanced system operations require **Shizuku** or **Root**. These are not required for every basic desktop feature. Do not configure them unless a feature you intend to use explicitly needs them and you understand the additional access they provide.

#### 5. Optional: make DevDex your default Home app

If you want the device to open into DevDex when you press Home:

1. Open Android **Settings → Apps → Default apps → Home app** (the labels may differ).
2. Select **DevDex**, if it is listed.
3. Keep your existing launcher available so you can switch back if needed.

You can skip this step and launch DevDex manually instead.

#### 6. First-run checks

- Open DevDex and confirm the desktop, taskbar, and dock appear.
- Try moving, resizing, restoring, and closing a windowed app.
- Check **Settings → Appearance** for status-bar preferences. The status bar is visible by default; **Hide status bar** is optional.
- If you use the built-in file manager on Android 11 or newer, it may require **All files access**. Grant it only if you intend to use that feature.

#### Troubleshooting

- **“App not installed” or update fails:** a different signing certificate may be the cause. Do not uninstall your current installation unless you have backed up what you need and accept that uninstalling may erase DevDex's local app data.
- **Desktop controls or overlays do not appear:** review the overlay permission and any Accessibility setup shown by DevDex. Restart the app after changing permissions.
- **Freeform windows do not work:** support depends on your Android version and the device firmware. Check the [Compatibility and limitations](#compatibility-and-limitations) section; some manufacturers disable freeform windows.
- **A permission screen looks different:** follow the Android prompt on your device. Manufacturer-specific menus can use different names or omit the feature entirely.

For reproducible bugs, include your device model, Android/firmware version, the steps you took, and screenshots or screen recordings in a [GitHub Issue](https://github.com/devboilol/devdex/issues).

### Settings backup

Open **Settings → Advanced → Backup/Restore Preferences** to export or import settings. New backups use JSON format; older `.sdp` backups remain supported.

### Safety notice

> **Recommended: use DevDex on a secondary device, preferably a tablet.** Accessibility and overlay features may conflict with banking, government, identity-verification, payment, enterprise-authentication, or other security-sensitive apps. Some apps may block access while overlays or desktop controls are active. Keep a regular launcher available and do not depend on DevDex for critical secure workflows on your primary device.

### Compatibility and limitations

- Designed for Android 8.0 and newer; actual behavior depends on the device and Android version.
- Selected system-level features and task/window operations may require Root or Shizuku.
- Some manufacturers disable freeform windows by default. On affected RedMagic/ZTE builds, enable **Force activities to be resizable** and **Enable freeform windows** in Developer options; some builds also require **Settings → Display → Desktop Mode**, followed by a reboot. Menu names and availability vary by OEM/firmware. DevDex cannot restore a feature removed by the ROM.
- **Settings → Advanced → Multitasking compatibility → Enable experimental freeform windows** is an opt-in attempt for ROMs that expose the underlying Android settings. It is not a universal workaround.
- Apps that lock their orientation may ignore normal launch orientation requests. The experimental **Ignore app orientation requests** switch uses a system-level WindowManager command where available, needs Shizuku/Root on supported ROMs, affects the whole system, and can break camera or portrait-only interfaces. It is off by default.
- Desktop widgets are off by default. Enable **Experimental desktop widgets** in Advanced settings to test them; behavior still depends on widget providers and OEM builds.
- The Android status bar is visible by default. **Settings → Appearance → Hide status bar** can hide it for desktop mode; the navigation bar remains immersive and can be temporarily revealed with a swipe.
- DevDex includes a rebuilt basic local file manager under **Settings → Default apps → Open DevDex file manager**. It supports folder navigation, opening/sharing files, rename/delete, multi-select, copy/move, ZIP creation, sorting, and search within the current folder. Android 11 and newer require the user to grant All files access. On ROMs that block this permission, use the system picker or another file manager. The MVP focuses on primary shared storage, not SD-card root browsing.
- Window controls and fullscreen detection depend partly on Android and device-manufacturer behavior. Please test on your own device.
- Custom Close/Resize controls are intended only for eligible top-level app windows; see the [window controls scope and device checklist](docs/WINDOW_CONTROLS_SCOPE.md).
- Always-on-top pinning for arbitrary apps is not implemented through the ordinary public Android APIs.
- Current hands-on validation is limited to the Redmi Pad SE 8.7. Other tablet models, Android Go builds, and OEM-specific compatibility switches remain unverified until tested on those devices.

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

### Trạng thái phát hành
DevDex đang chuẩn bị bản Release Candidate (RC). APK từ CI là bản kiểm thử; GitHub pre-release chỉ được xuất bản từ tag phiên bản khi đã cấu hình khóa ký ổn định. Xem [quy trình RC](docs/RELEASE_CANDIDATE_FLOW.md) để kiểm tra điều kiện phát hành.

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

### Hướng dẫn cài đặt chi tiết

> **Lưu ý về bản thử nghiệm:** Đây là bản Community Preview đang trong giai đoạn thử nghiệm, chưa phải bản ổn định. APK dùng debug signing nên có thể không cài cập nhật đè lên một bản DevDex khác. Hãy sao lưu dữ liệu quan trọng trước và ưu tiên thử trên thiết bị phụ. Không dùng bản này cho ngân hàng, thanh toán, xác minh danh tính, dịch vụ chính phủ hoặc các thao tác nhạy cảm khác.

#### 1. Tải đúng file APK

1. Mở trang phát hành [DevDex 3.5.186 Community Preview](https://github.com/devboilol/devdex/releases/tag/v3.5.186-preview.1).
2. Trong mục **Assets**, tải file **`DevDex.3.5.186.Community.Preview.apk`**.
3. Khi cài ứng dụng, **không** tải hai file **Source code**. Các file ZIP/TAR chứa mã nguồn, không phải bộ cài APK.

#### 2. Cho phép cài ứng dụng từ nguồn tải xuống

1. Mở file APK vừa tải trong danh sách tải xuống của trình duyệt hoặc bằng trình quản lý tệp.
2. Nếu Android chặn cài đặt, làm theo hướng dẫn để mở Cài đặt và bật **Allow from this source / Install unknown apps** cho đúng trình duyệt hoặc trình quản lý tệp đang dùng.
3. Quay lại APK và tiếp tục cài. Tên mục và giao diện có thể khác nhau theo phiên bản Android và hãng thiết bị. Sau khi cài xong, bạn có thể tắt lại quyền này.

Chỉ cài APK từ nguồn đáng tin cậy. Đây là bản thử nghiệm sớm.

#### 3. Cài đặt và mở DevDex

1. Nhấn **Install / Cài đặt** và chờ Android cài xong.
2. Nhấn **Open / Mở**.
3. Hoàn tất thiết lập lần đầu và chọn **System default**, **English** hoặc **Vietnamese**.

#### 4. Xem lại các quyền được yêu cầu

- **Display over other apps / Appear on top (Hiển thị trên ứng dụng khác):** bật quyền này cho DevDex khi được yêu cầu nếu m muốn lớp phủ desktop, taskbar và điều khiển cửa sổ hoạt động.
- **Accessibility (Trợ năng):** nếu DevDex hướng dẫn bật dịch vụ Trợ năng, hãy đọc giải thích của Android rồi chỉ bật nếu bạn cần các tương tác desktop liên quan. Có thể xem lại hoặc thu hồi trong Cài đặt Android → Trợ năng.
- Chỉ cấp quyền cho DevDex và khi cần thiết. Tên và vị trí các mục cài đặt có thể khác theo hãng máy.

Một số thao tác hệ thống nâng cao cần **Shizuku** hoặc **Root**. Các tính năng desktop cơ bản không phải lúc nào cũng cần những quyền này. Đừng cấu hình nếu tính năng bạn định dùng không yêu cầu và m chưa rõ mức truy cập mà chúng cung cấp.

#### 5. Tùy chọn: đặt DevDex làm ứng dụng Màn hình chính mặc định

Nếu muốn thiết bị mở DevDex khi nhấn nút Home:

1. Mở **Cài đặt Android → Ứng dụng → Ứng dụng mặc định → Ứng dụng Màn hình chính / Home app** (tên mục có thể khác).
2. Chọn **DevDex** nếu ứng dụng xuất hiện trong danh sách.
3. Giữ trình khởi chạy hiện tại để có thể chuyển lại khi cần.

Có thể bỏ qua bước này và tự mở DevDex khi muốn sử dụng.

#### 6. Kiểm tra sau khi cài

- Mở DevDex và kiểm tra desktop, taskbar cùng dock có xuất hiện không.
- Thử di chuyển, đổi kích thước, khôi phục và đóng một ứng dụng dạng cửa sổ.
- Kiểm tra **Cài đặt → Appearance** về thanh trạng thái. Thanh trạng thái hiển thị mặc định; **Hide status bar** là tùy chọn.
- Nếu dùng trình quản lý tệp tích hợp trên Android 11 trở lên, ứng dụng có thể yêu cầu quyền **All files access (Truy cập tất cả tệp)**. Chỉ cấp nếu bạn định dùng tính năng này.

#### Khắc phục sự cố

- **“App not installed” / không thể cài cập nhật:** có thể do chứng thư ký khác nhau. Đừng gỡ bản đang dùng nếu chưa sao lưu dữ liệu cần giữ và chưa chấp nhận khả năng dữ liệu cục bộ của DevDex bị xóa khi gỡ.
- **Không thấy điều khiển cửa sổ hoặc lớp phủ:** kiểm tra quyền hiển thị trên ứng dụng khác và phần thiết lập Trợ năng mà DevDex hướng dẫn. Thử mở lại ứng dụng sau khi đổi quyền.
- **Cửa sổ tự do (freeform) không hoạt động:** tính năng phụ thuộc phiên bản Android và firmware. Xem mục [Tương thích và giới hạn](#tương-thích-và-giới-hạn); một số hãng tắt sẵn freeform windows.
- **Màn hình cấp quyền khác hướng dẫn:** làm theo thông báo Android trên thiết bị của bạn. Menu riêng của từng hãng có thể khác tên hoặc không có tính năng đó.

Khi báo lỗi, hãy nêu model thiết bị, phiên bản Android/firmware, các bước tái hiện và đính kèm ảnh hoặc video trong [GitHub Issue](https://github.com/devboilol/devdex/issues).

### Sao lưu cài đặt

Mở **Cài đặt → Advanced → Backup/Restore Preferences** để xuất hoặc nhập cài đặt. Bản sao lưu mới dùng định dạng JSON; các bản `.sdp` cũ vẫn được hỗ trợ.

### Cảnh báo an toàn

> **Khuyến nghị dùng DevDex trên thiết bị phụ, tốt nhất là máy tính bảng.** Các tính năng Trợ năng và hiển thị đè có thể xung đột với ứng dụng ngân hàng, dịch vụ chính phủ, xác minh danh tính, thanh toán, xác thực doanh nghiệp hoặc ứng dụng nhạy cảm về bảo mật. Một số ứng dụng có thể chặn truy cập khi lớp phủ hoặc nút điều khiển cửa sổ đang hoạt động. Hãy giữ lại trình khởi chạy thông thường và không phụ thuộc vào DevDex cho các thao tác bảo mật quan trọng trên thiết bị chính.

### Tương thích và giới hạn

- Hướng tới Android 8.0 trở lên; hành vi thực tế phụ thuộc thiết bị và phiên bản Android.
- Một số tính năng hệ thống hoặc thao tác quản lý tác vụ/cửa sổ có thể cần Root hoặc Shizuku.
- Thanh trạng thái Android hiển thị mặc định. Vào **Cài đặt → Appearance → Hide status bar** để ẩn khi dùng desktop mode; thanh điều hướng vẫn ở chế độ immersive và có thể hiện tạm thời khi vuốt.
- Một số nhà sản xuất tắt sẵn chế độ cửa sổ tự do (freeform windows). Trên một số bản ROM RedMagic/ZTE, hãy bật **Force activities to be resizable** và **Enable freeform windows** trong Tùy chọn nhà phát triển; một số bản còn yêu cầu **Cài đặt → Màn hình → Desktop Mode** rồi khởi động lại. Tên mục và khả năng có sẵn tùy OEM/firmware. DevDex không thể khôi phục tính năng đã bị ROM loại bỏ.
- **Cài đặt → Advanced → Tương thích đa nhiệm → Bật cửa sổ tự do thử nghiệm** là nỗ lực tùy chọn dành cho ROM vẫn cung cấp thiết lập Android tương ứng, không phải cách sửa phổ quát.
- Ứng dụng tự khóa hướng màn hình có thể bỏ qua yêu cầu xoay thông thường. Công tắc thử nghiệm **Bỏ qua yêu cầu xoay màn hình của ứng dụng** dùng lệnh hệ thống WindowManager trên ROM hỗ trợ, cần Shizuku/Root, ảnh hưởng toàn hệ thống và có thể làm giao diện camera hoặc ứng dụng chỉ chạy dọc hoạt động sai. Mặc định tắt.
- Tiện ích desktop mặc định tắt. Bật **Tiện ích desktop (thử nghiệm)** trong Cài đặt nâng cao để thử; hành vi vẫn phụ thuộc provider và ROM.
- DevDex có trình quản lý tệp cục bộ cơ bản được dựng lại tại **Cài đặt → Ứng dụng mặc định → Mở trình quản lý tệp DevDex**. Tính năng gồm duyệt thư mục, mở/chia sẻ, đổi tên/xóa, chọn nhiều tệp, sao chép/di chuyển, tạo ZIP, sắp xếp và tìm trong thư mục hiện tại. Android 11 trở lên cần được cấp quyền Truy cập tất cả tệp. Nếu ROM chặn quyền này, hãy dùng trình chọn tệp hệ thống hoặc trình quản lý tệp khác. Bản MVP tập trung vào bộ nhớ dùng chung chính, chưa duyệt thư mục gốc của thẻ SD.
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
