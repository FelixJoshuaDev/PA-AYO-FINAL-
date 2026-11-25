/*
 * File:        EndToEndIT.java
 * Module:      Testing
 * Purpose:     Drives the running site in Chrome with Playwright (TC-01, TC-02, TC-04 to TC-06, TC-12, TC-14, TC-16, TC-17).
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.ReducedMotion;
import com.paayo.model.Admin;
import com.paayo.model.Category;
import com.paayo.repository.AdminRepository;
import com.paayo.repository.NewsRepository;
import com.paayo.repository.ReportRepository;
import com.paayo.testutil.Emulator;
import com.paayo.testutil.Photos;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Browser tests on the sample data; needs the Firestore emulator and Google Chrome. Mail and Cloudinary are faked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pa-ayo.firebase.emulator-host=" + Emulator.HOST,
        "pa-ayo.firebase.project-id=" + Emulator.PROJECT_ID,
        "pa-ayo.news.source-urls=",
        "pa-ayo.admin.username=qa.admin",
        "pa-ayo.admin.email=qa.admin@example.com",
        "pa-ayo.admin.password=first-password"})
@ActiveProfiles("seed")
class EndToEndIT {
    private static HttpServer cloudinary;
    private static final List<String> cloudinaryCalls = new CopyOnWriteArrayList<>();
    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;
    @Autowired
    private ReportRepository reports;
    @Autowired
    private NewsRepository news;
    @Autowired
    private AdminRepository admins;
    @MockitoBean
    private JavaMailSender mailSender;
    private Page page;

    @DynamicPropertySource
    static void prepare(DynamicPropertyRegistry registry) throws Exception {
        Emulator.clear();
        cloudinary = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        String base = "http://127.0.0.1:" + cloudinary.getAddress().getPort();
        cloudinary.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            cloudinaryCalls.add(exchange.getRequestURI().getPath());
            byte[] body = exchange.getRequestMethod().equals("POST")
                    ? ("{\"secure_url\":\"" + base + "/pa-ayo/photo.png\"}").getBytes(StandardCharsets.UTF_8) : samplePhoto();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        cloudinary.start();
        registry.add("pa-ayo.cloudinary.url", () -> "cloudinary://key:secret@demo?upload_prefix=" + base);
    }

    private static byte[] samplePhoto() throws IOException {
        try (InputStream in = EndToEndIT.class.getResourceAsStream("/static/images/samples/roadworks.png")) {
            return in.readAllBytes();
        }
    }

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
    }

    @AfterAll
    static void stopBrowser() {
        playwright.close();
        cloudinary.stop(0);
    }

    @BeforeEach
    void openPage() {
        page = browser.newPage(new Browser.NewPageOptions().setBaseURL("http://localhost:" + port));
        page.setViewportSize(1470, 830);
    }

    @AfterEach
    void closePage() {
        page.close();
    }

    private Locator cards() {
        return page.locator(".card");
    }

    private void fillForm(String title) {
        page.navigate("/reports/new");
        page.getByLabel("Report Title*").fill(title);
        page.getByLabel("Issue Category*").selectOption("ROADWORKS");
        page.getByLabel("Location*").fill("A.S. Fortuna St., Banilad, Mandaue City");
        page.getByLabel("Description*").fill("Deep pothole in the right lane");
    }

    private void submit() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit Report")).click();
    }

    private void logIn(String password) {
        page.navigate("/admin/login");
        page.getByLabel("Username").fill("qa.admin");
        page.locator("#password").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("SIGN IN")).click();
    }

    private int gridColumns() {
        return ((String) page.locator(".grid").last().evaluate("g => getComputedStyle(g).gridTemplateColumns")).split(" ").length;
    }

    /** TC-12: the fixtures of DDS 10.2 are stored. */
    @Test
    void sampleDataIsComplete() {
        for (Category category : Category.values()) {
            assertThat(reports.findByCategory(category)).hasSizeGreaterThanOrEqualTo(10)
                    .anyMatch(report -> report.getTitle().length() == 100 && report.getLocation().length() == 200
                            && report.getDescription().length() == 1000 && report.isValid());
        }
        assertThat(news.findLatest(10)).hasSize(6);
        assertThat(admins.findByUsername("expired.admin")).isNotNull();
    }

    /** TC-12: 30+ cards keep the 4, 2 and 1 column layout without overflowing. */
    @Test
    void gridIsResponsive() {
        page.navigate("/");
        assertThat(cards().count()).isGreaterThanOrEqualTo(30);
        for (int[] rule : new int[][] {{1470, 4}, {1200, 4}, {1199, 2}, {768, 2}, {767, 1}, {375, 1}}) {
            page.setViewportSize(rule[0], 830);
            assertThat(gridColumns()).as("columns at %d px", rule[0]).isEqualTo(rule[1]);
            assertThat(page.evaluate("document.documentElement.scrollWidth <= innerWidth"))
                    .as("no sideways scrolling at %d px", rule[0]).isEqualTo(true);
        }
    }

    /** FR-07: the newest news is in the main area, also while a category is selected. */
    @Test
    void showsHotNewsInTheMainArea() {
        for (String path : new String[] {"/", "/?category=ELECTRICAL"}) {
            page.navigate(path);
            assertThat(page.locator("main .hot-card")).hasCount(4);
            assertThat(page.locator("aside .news-item")).hasCount(2);
        }
    }

    /** The news sidebar always ends inside the window, at the top of the page and after scrolling. */
    @Test
    void sidebarFitsTheScreen() {
        for (int[] size : new int[][] {{1470, 830}, {1366, 768}, {1280, 360}, {768, 1024}}) {
            page.setViewportSize(size[0], size[1]);
            page.navigate("/");
            for (int scroll : new int[] {0, 900}) {
                page.evaluate("y => scrollTo({top: y, behavior: 'instant'})", scroll);
                assertThat(page.evaluate("document.querySelector('.news').getBoundingClientRect().bottom <= innerHeight"))
                        .as("sidebar at %d x %d, scrolled %d", size[0], size[1], scroll).isEqualTo(true);
            }
        }
    }

    /** Visitors who ask their device for less movement get none. */
    @Test
    void respectsReducedMotion() {
        page.emulateMedia(new Page.EmulateMediaOptions().setReducedMotion(ReducedMotion.REDUCE));
        page.navigate("/");

        assertThat(page.evaluate("getComputedStyle(document.querySelector('.card')).animationName")).isEqualTo("none");
    }

    /** TC-01 and TC-16b: a report without a photo is the first card and shows the placeholder. */
    @Test
    void submitsAReportWithoutAPhoto() {
        page.navigate("/");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Report an Issue")).click();
        fillForm("Pothole without photo");
        submit();

        assertThat(page).hasURL("http://localhost:" + port + "/");
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("Your report was submitted");
        page.getByText("See your report").click();
        assertThat(page.locator("#new-report")).containsText("Pothole without photo");
        assertThat(cards().first()).containsText("Pothole without photo");
        assertThat(cards().first()).containsText("Reported");
        assertThat(cards().first().locator("img")).hasAttribute("src", "/images/report-placeholder.png");
    }

    /** TC-16a: a 1 MB JPEG is stored and shown on the card. */
    @Test
    void submitsAReportWithAPhoto() throws IOException {
        fillForm("Pothole with photo");
        page.locator("#photoFile").setInputFiles(
                new FilePayload("photo.jpg", "image/jpeg", Photos.of("image/jpeg", 1024 * 1024).getBytes()));
        submit();

        assertThat(cards().first()).containsText("Pothole with photo");
        assertThat(cards().first().locator("img")).hasAttribute("src", Pattern.compile(".*/photo.png"));
    }

    /** The button locks while a report is being sent, so a second click cannot duplicate it, and unlocks on return. */
    @Test
    void blocksASecondSubmit() {
        page.navigate("/reports/new");
        page.locator("form").dispatchEvent("submit"); // runs the page's handler without leaving the page
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submitting..."))).isDisabled();

        page.evaluate("window.dispatchEvent(new Event('pageshow'))"); // what the browser fires on Back
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit Report"))).isEnabled();
    }

    /** TC-16c: a PDF and a 6 MB photo are refused with an inline error, before and after upload. */
    @Test
    void refusesInvalidPhotos() throws IOException {
        fillForm("Refused");
        page.locator("#photoFile").setInputFiles(new FilePayload("a.pdf", "application/pdf", "%PDF".getBytes()));
        assertThat(page.locator("#photo-error")).containsText("JPEG, PNG or WebP");
        FilePayload large = new FilePayload("b.jpg", "image/jpeg", Photos.of("image/jpeg", 6 * 1024 * 1024).getBytes());
        page.locator("#photoFile").setInputFiles(large);
        assertThat(page.locator("#photo-error")).containsText("5 MB");

        page.route("**/js/report-form.js", route -> route.abort());
        fillForm("Refused");
        page.locator("#photoFile").setInputFiles(large);
        submit();
        assertThat(page.locator("#photo-error")).containsText("5 MB");
        page.navigate("/");
        assertThat(page.getByText("Refused")).hasCount(0);
    }

    /** Only an admin can delete a report; its photo goes with it, after a confirmation. */
    @Test
    void adminDeletesAReport() throws IOException {
        fillForm("Delete this report");
        page.locator("#photoFile").setInputFiles(
                new FilePayload("photo.jpg", "image/jpeg", Photos.of("image/jpeg", 1024).getBytes()));
        submit();
        assertThat(page.getByRole(AriaRole.BUTTON)).hasCount(0); // the public page has no delete button
        logIn("first-password");
        Locator row = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText("Delete this report"));
        Locator delete = row.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete"));

        page.onceDialog(Dialog::dismiss);
        delete.click();
        assertThat(row).hasCount(1);
        page.onceDialog(Dialog::accept);
        delete.click();

        assertThat(page.getByText("Report deleted")).isVisible();
        assertThat(row).hasCount(0);
        assertThat(cloudinaryCalls).anyMatch(call -> call.endsWith("/destroy"));
        page.navigate("/");
        assertThat(page.getByText("Delete this report")).hasCount(0);
    }

    /** TC-02: empty fields show inline errors. */
    @Test
    void showsInlineErrors() {
        page.navigate("/reports/new");
        submit();

        assertThat(page.locator(".field-error:not(:empty)")).hasCount(4);
        assertThat(page.getByText("Prototype System: Do not enter sensitive personal information.")).isVisible();
    }

    /** TC-04: the category pills filter the grid and an unknown category shows everything. */
    @Test
    void filtersByCategory() {
        page.navigate("/");
        page.locator(".pills").getByText("Electrical").click();
        assertThat(page.locator(".badge-electrical").first()).isVisible();
        assertThat(page.locator(".badge-sanitation, .badge-roadworks")).hasCount(0);

        page.navigate("/?category=INVALID");
        assertThat(cards().count()).isGreaterThanOrEqualTo(30);
    }

    /** TC-14: a script in a report is shown as text and never runs. */
    @Test
    void escapesScripts() {
        page.onDialog(dialog -> {
            throw new AssertionError("script ran");
        });
        fillForm("<script>alert(1)</script>");
        submit();

        assertThat(cards().first()).containsText("<script>alert(1)</script>");
    }

    /** TC-14: a form without a CSRF token is refused with a friendly page. */
    @Test
    void refusesAFormWithoutAToken() {
        fillForm("No token");
        page.evaluate("const f = document.forms[0]; f.action = '/reports/new'; f.elements._csrf.remove()");
        submit();

        assertThat(page.getByText("This form has expired")).isVisible();
    }

    /** TC-05 and TC-06: only a signed-in admin can change a status, and the public badge follows. */
    @Test
    void adminUpdatesAStatus() {
        fillForm("Status change check");
        submit();
        page.navigate("/admin/dashboard");
        assertThat(page).hasURL(Pattern.compile(".*/admin/login"));
        logIn("wrong-password");
        assertThat(page.getByText("Invalid username or password")).isVisible();
        logIn("first-password");
        assertThat(page).hasURL(Pattern.compile(".*/admin/dashboard"));

        for (String status : new String[] {"In Progress", "Resolved"}) {
            Locator row = page.locator("tbody tr").filter(new Locator.FilterOptions().setHasText("Status change check"));
            row.locator("select").selectOption(status.toUpperCase().replace(' ', '_'));
            row.getByText("Update Status").click();
            assertThat(page.getByText("Status updated successfully")).isVisible();
            assertThat(row.locator("select option:checked")).hasText(status);
        }
        page.getByText("Pa-Ayo").first().click();
        assertThat(cards().filter(new Locator.FilterOptions().setHasText("Status change check"))).containsText("Resolved");

        page.goBack();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Logout")).click();
        page.navigate("/admin/dashboard");
        assertThat(page).hasURL(Pattern.compile(".*/admin/login"));
    }

    /** UC-04: the fetch button runs the bot. No sources are configured here, so nothing is new. */
    @Test
    void adminRunsTheNewsBot() {
        logIn("first-password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Fetch Latest News Now")).click();

        assertThat(page.getByText("0 new items")).isVisible();
    }

    /** TC-17: the emailed link sets a new password once; the old password and the spent link stop working. */
    @Test
    void recoversAForgottenPassword() {
        Admin other = new Admin();
        other.setUsername("second.admin");
        other.setEmail("second.admin@example.com");
        other.setPasswordHash("not-a-bcrypt-hash");
        admins.save(other);

        for (String identifier : new String[] {"stranger", "Second.Admin@example.com"}) {
            page.navigate("/admin/forgot-password");
            page.getByLabel("Username or Admin Official Email").fill(identifier);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send Reset Link")).click();
            assertThat(page.getByRole(AriaRole.STATUS)).hasText(
                    "If an admin account matches your entry, a password reset link has been sent.");
        }
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(3000).times(1)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("second.admin@example.com");
        String link = "/admin/reset-password?token=" + mail.getValue().getText().split("token=")[1].split("\\s")[0];

        page.navigate(link);
        page.locator("#newPassword").fill("brand-new-password");
        page.locator("#confirmPassword").fill("different-password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset Password")).click();
        assertThat(page.getByRole(AriaRole.ALERT)).hasText("The two passwords do not match.");
        page.locator("#newPassword").fill("brand-new-password");
        page.locator("#confirmPassword").fill("brand-new-password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset Password")).click();
        assertThat(page.getByText("Your password was changed")).isVisible();

        page.navigate(link);
        assertThat(page.getByText("invalid, has expired or was already used")).isVisible();
        page.navigate("/admin/login");
        page.getByLabel("Username").fill("second.admin");
        page.locator("#password").fill("brand-new-password");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("SIGN IN")).click();
        assertThat(page).hasURL(Pattern.compile(".*/admin/dashboard"));
    }

    /** The eye button shows and hides the password. */
    @Test
    void showsThePasswordOnRequest() {
        page.navigate("/admin/login");
        page.getByLabel("Show password").click();

        assertThat(page.locator("#password")).hasAttribute("type", "text");
    }
}
