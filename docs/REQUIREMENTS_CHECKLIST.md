# Requirements checklist

Sources: **SRS** = Software Requirements Specification v1.2, **DDS** = Database Design Specification v1.2, **PROP** = Project Proposal v0.9.

Status: **Done** = implemented and covered by an automated test. **Live check** = implemented, but still to be confirmed on the deployed site. **Manual** = a documented manual step.

Paths are shortened: `java/` is `src/main/java/com/paayo/`, `templates/` and `static/` are under `src/main/resources/`, and `test/` is `src/test/java/com/paayo/`.

## Functional requirements

| # | ID | Requirement | Source | Implemented in | Tests | Status |
|---|---|---|---|---|---|---|
| 1 | FR-01 | Submit a report without an account | SRS, PROP | `java/controller/ReportController`, `java/service/ReportService`, `templates/report-form.html` | `ReportControllerTest`, `EndToEndIT` | Done |
| 2 | FR-02 | Exactly one of three categories | SRS, PROP, DDS | `java/model/Category`, `ReportController.initBinder` | `ReportControllerTest` | Done |
| 3 | FR-03 | Store reports in Firestore; 30 test reports | SRS, PROP, DDS | `java/repository/ReportRepository`, `java/config/SampleDataLoader` | `RepositoryIT`, `EndToEndIT` | Done |
| 4 | FR-04 | New report is the first card within 2 s | SRS, PROP | `ReportController.showDashboard`, `ReportRepository.findAll`, `templates/dashboard.html` | `EndToEndIT`, `RepositoryIT` | Done |
| 5 | FR-05 | Admin updates the status | SRS, PROP, DDS | `java/controller/AdminController`, `ReportService.updateReportStatus`, `templates/admin/dashboard.html` | `AdminControllerTest`, `EndToEndIT` | Done |
| 6 | FR-06 | Jsoup bot on a schedule or by admin trigger | SRS, PROP | `java/newsbot/NewsScraper`, `java/newsbot/NewsScheduler`, `AdminController.triggerNewsFetch` | `NewsScraperTest`, `NewsSchedulerTest`, `AdminControllerTest` | Done |
| 7 | FR-07 | Condense and publish at least 2 items without duplicates | SRS, PROP, DDS | `java/newsbot/TextParaphraser`, `java/service/NewsService`, `java/repository/NewsRepository` | `TextParaphraserTest`, `NewsServiceTest` | Done |
| 8 | FR-08 | Validate fields and photo | SRS, PROP, DDS | `java/model/Report`, `ReportService.validateReport`, `ImageStorageService.validatePhoto`, `static/js/report-form.js` | `ReportTest`, `ReportControllerTest`, `ImageStorageServiceTest`, `EndToEndIT` | Done |
| 9 | FR-09 | Public titles, statuses and category filter | SRS, PROP, DDS | `ReportController.filterByCategory`, `ReportRepository.findByCategory`, `templates/dashboard.html` | `ReportControllerTest`, `EndToEndIT` | Done |
| 10 | FR-10 | At least 80% line coverage on four classes | SRS, PROP | `pom.xml` (JaCoCo gate) | `mvn test` fails below 80% | Done |
| 11 | FR-11 | Live on Render over HTTPS by 4 Dec 2026 | SRS, PROP | `Dockerfile`, `docs/DEPLOYMENT.md` | TC-15 | Live check |
| 12 | FR-12 | Optional photo and placeholders | SRS, PROP, DDS | `java/service/ImageStorageService`, `java/config/CloudinaryConfig`, `static/images/` | `ImageStorageServiceTest`, `EndToEndIT` (Cloudinary faked); real JPEG, PNG and WebP uploads checked on Cloudinary on 24 Nov 2026 | Done |
| 13 | FR-13 | Admin password recovery | SRS, PROP, DDS | `java/service/AdminAuthService`, `AdminController`, `java/repository/AdminRepository`, `templates/admin/` | `AdminAuthServiceTest`, `AdminControllerTest`, `RepositoryIT`, `EndToEndIT` (mail faked) | Live check |

## Non-functional requirements

| # | ID | Requirement | Source | Implemented in | Tests | Status |
|---|---|---|---|---|---|---|
| 14 | NFR-01 | Pages render within 3 s | SRS, PROP | Query limits, scaled photos, lazy images, small thumbnails | Timing on the deployed site | Live check |
| 15 | NFR-02 | 5 s timeout with a retry message | SRS, PROP, DDS | `java/repository/FirestoreCalls`, `java/config/CloudinaryConfig`, controllers | `FirestoreCallsTest`, `ReportControllerTest` | Done |
| 16 | NFR-03 | 10 s scraper timeout; errors isolated | SRS, PROP, DDS | `NewsScraper.scrape`, `NewsScheduler` | `NewsScraperTest`, `NewsSchedulerTest` | Done |
| 17 | NFR-04 | 30+ cards without distortion | SRS, PROP, DDS | `static/css/style.css` | `EndToEndIT.gridIsResponsive` | Done |
| 18 | NFR-05 | HTTPS and TLS | SRS, PROP, DDS | Render, Firebase SDK, `application.properties` (forwarded headers) | TC-15 | Live check |
| 19 | NFR-06 | BCrypt; hashed 30-minute single-use tokens | SRS, PROP, DDS | `AdminAuthService`, `AdminRepository.updatePasswordHash`, `SecurityConfig` | `AdminAuthServiceTest`, `RepositoryIT`, `EndToEndIT` | Done |
| 20 | NFR-07 | Admin routes need a session | SRS, PROP, DDS | `java/config/SecurityConfig` | `AdminControllerTest`, `EndToEndIT` | Done |
| 21 | NFR-08 | No secrets in Git | SRS, PROP, DDS | `.gitignore`, `.dockerignore`, `.env.example` | Repository review | Done |
| 22 | NFR-09 | Server validation, escaping, CSRF | SRS, PROP, DDS | `ReportController`, `ImageStorageService`, `th:text` in every template, `SecurityConfig` | `ReportControllerTest`, `EndToEndIT` | Done |
| 23 | NFR-10 | No personal data; privacy notice | SRS, PROP, DDS | `java/model/Report`, `templates/report-form.html` | `ReportControllerTest`, `EndToEndIT` | Done |
| 24 | NFR-11 | Never publish the same URL twice | SRS, PROP, DDS | `NewsService.fetchAndPublishNews`, `NewsRepository.existsBySourceUrl` | `NewsServiceTest`, `RepositoryIT` | Done |
| 25 | NFR-12 | Submit in 4 (SRS) or 5 (PROP) interactions | SRS, PROP | One form: title, category, location and description, submit | UAT | Live check |
| 26 | NFR-13 | Status colors with text labels | SRS, PROP, DDS | `static/css/style.css`, `java/model/ReportStatus` | `ReportControllerTest`, UAT | Done |
| 27 | NFR-14 | Strict layering; selectors only in NewsScraper | SRS, PROP, DDS | Package structure | Code review | Done |
| 28 | NFR-15 | Offline fixtures; mocked Firestore, storage and mail | SRS, PROP, DDS | `src/main/resources/samples`, `src/test/resources/fixtures` | All unit tests | Done |
| 29 | NFR-16 | Asia/Manila times | SRS, PROP, DDS | `java/util/FirestoreTypes` | `FirestoreTypesTest` | Done |
| 30 | NFR-17 | Mandaue, Metro Cebu and Central Visayas scope | SRS, PROP | `application.properties` (news sources), `docs/NEWS_SOURCES.md` | Configuration review | Done |

## Other mandatory items

| # | Requirement | Source | Implemented in | Tests | Status |
|---|---|---|---|---|---|
| 31 | 4, 2 and 1 columns at 1200 and 768 px | SRS 3.8, PROP | `static/css/style.css` | `EndToEndIT.gridIsResponsive` | Done |
| 32 | WCAG 2.1 AA | PROP | Templates, `static/css/style.css` | Checked with axe-core on 24 Nov 2026: no violations on S1, S2, S3, S3b (request page) and S4 | Done |
| 33 | Deny-all rules and composite index | SRS 3.6.3, DDS 8 | `firestore.rules`, `firestore.indexes.json`, `firebase.json` | Checked on the production project on 24 Nov 2026: outside access refused, category filter works | Done |
| 34 | Seed the first admin from the environment | SRS 3.6.3, DDS 9.3 | `java/config/AdminSeeder` | `AdminSeederTest` | Done |
| 35 | Same recovery reply for every entry | SRS UC-07, DDS 8.3 | `AdminController.requestPasswordReset`, `AdminAuthService` | `AdminControllerTest`, `AdminAuthServiceTest`, `EndToEndIT` | Done |
| 36 | Visitors cannot set protected fields; createdAt never changes | DDS 8.3 | `ReportController.initBinder`, `ReportRepository.updateStatus` | `ReportControllerTest`, `RepositoryIT` | Done |
| 37 | Limits of 100 reports and 10 news items | SRS 3.6.3, DDS 7 | `ReportRepository`, `ReportController` | Code review | Done |
| 38 | Daily 06:00 run plus a startup run | SRS 5.1, DDS 9.1 | `NewsScheduler`, `application.properties` | `NewsSchedulerTest` | Done |
| 39 | Firestore emulator on port 8085 | DDS 2.3 | `firebase.json`, `java/config/FirebaseConfig` | `RepositoryIT` | Done |
| 40 | 30 reports, 6 news items, 2 admins as sample data | DDS 10.2 | `java/config/SampleDataLoader`, `AdminSeeder` | `EndToEndIT.sampleDataIsComplete` | Done |
| 41 | Test cases TC-01 to TC-17 | SRS 6.2, PROP | See the next table | | TC-15 and UAT pending |
| 42 | Purge Resolved reports older than 90 days | SRS 5.2, DDS 9.4, PROP | `docs/DATABASE_SETUP.md` (Maintenance) | | Manual |
| 43 | An authenticated admin deletes a report and its photo, after a confirmation | Team decision, 25 Nov 2026; supports PROP Risk 7 and Rollback Plan | `AdminController.deleteReport`, `ReportService.deleteReport`, `ReportRepository.delete`, `ImageStorageService.deletePhoto`, `templates/admin/dashboard.html`, `static/js/confirm.js` | `AdminControllerTest`, `ReportServiceTest`, `ImageStorageServiceTest`, `RepositoryIT`, `EndToEndIT`; real photo deletion checked on Cloudinary | Done |

## Test cases

| Test case | Automated in |
|---|---|
| TC-01 | `ReportServiceTest`, `ReportControllerTest`, `EndToEndIT` |
| TC-02, TC-03 | `ReportTest`, `ReportServiceTest`, `ReportControllerTest`, `EndToEndIT` |
| TC-04 | `ReportControllerTest`, `RepositoryIT`, `EndToEndIT` |
| TC-05 | `ReportTest`, `ReportServiceTest`, `RepositoryIT`, `AdminControllerTest`, `EndToEndIT` |
| TC-06 | `AdminControllerTest`, `AdminAuthServiceTest`, `EndToEndIT` |
| TC-07, TC-08 | `NewsServiceTest`, `NewsScraperTest` |
| TC-09 | `NewsScraperTest`, `NewsServiceTest` |
| TC-10 | `NewsScraperTest`, `NewsSchedulerTest` |
| TC-11 | `TextParaphraserTest` |
| TC-12 | `EndToEndIT` |
| TC-13 | `mvn test` (JaCoCo gate) |
| TC-14 | `ReportControllerTest`, `EndToEndIT` |
| TC-15 | Manual, after deployment: `docs/DEPLOYMENT.md` |
| TC-16 | `ImageStorageServiceTest`, `ReportServiceTest`, `EndToEndIT` |
| TC-17 | `AdminAuthServiceTest`, `AdminControllerTest`, `RepositoryIT`, `EndToEndIT` |
| UAT | Manual (WBS 1.4.5) |

Record the observed results in the team's QA report during Phase 4.

## Where the code differs from the documents

| Document says | Code does | Reason |
|---|---|---|
| Firebase Storage for photos (SRS 3.1) | Cloudinary free plan | Firebase Storage needs the paid Blaze plan. SRS Appendix B #10 names Cloudinary as the alternative. |
| Storage timeout over 5 s shows an error (SRS UC-01 E2) | Error after 5 s without an answer from Cloudinary; a slow upload may take longer | A hard 5 s limit would refuse valid photos near 5 MB on slow connections (FR-12) |
| Port 587 or 465 for email (SRS Appendix B #11) | Brevo on port 2525 | Render's free plan blocks 25, 465 and 587 |
| DPWH Region VII and Mandaue PIO pages (DDS 9.1) | Province of Cebu pages | See `docs/NEWS_SOURCES.md` |
| All news in the sidebar (SRS 3.8, mockup S1) | The newest four are Hot News cards in the main area; older items stay in the sidebar | Team decision on 25 Nov 2026; FR-07 is unchanged |
| Spam removed only in the Firebase Console (SRS 5.2, PROP Rollback Plan) | Admins also have a Delete button; residents do not | Team decision on 25 Nov 2026; accounts for residents stay out of scope |
| No keyword filter | Keyword list for infrastructure news | FR-06 asks for infrastructure advisories, and the source is general |
| Article text comes from the listing (SRS 3.7.3) | Read from the article page when the listing has none | The listings show only titles |
| 15-minute reset link (mockup) | 30 minutes | FR-13 and NFR-06 |
| No title field, several files (mockup S2) | Title field, one photo | FR-01 and FR-12 |
| Spring Security form login (SRS 3.5.3, recommended) | `AdminController.login()` inside Spring Security | Matches the UML class diagram |
