# Pa-Ayo

**Atong komunidad. Atong responsibilidad.**

Pa-Ayo is a digital bulletin board for Mandaue City and Metro Cebu. Residents report public maintenance issues such as clogged drains, uncollected garbage, damaged roads and broken streetlights, and everyone can follow each report until it is resolved. A news bot adds official infrastructure news to the same page.

CpE 3121 Software Development Strategies, University of San Carlos, AY 2026–2027, 1st Semester. Instructor: Engr. Efren Jr. Duran.

## Features

**Residents (no account)**
- Submit a report: title, category (Sanitation, Roadworks, Electrical), location, description and an optional photo (JPEG, PNG or WebP, up to 5 MB)
- See all reports, newest first, on a responsive grid (4, 2 or 1 columns)
- Filter by category and read the latest government news

**Administrators**
- Sign in, change a report's status (Reported, In Progress, Resolved), delete spam or unsuitable reports and run the news bot
- Recover a forgotten password with a single-use email link that expires in 30 minutes

**News bot**
- Runs at startup and every day at 6:00 AM (Asia/Manila)
- Collects infrastructure news, condenses it to 300 characters or less and never publishes the same article twice

## Tech stack

| Part | Technology |
|---|---|
| Language and framework | Java 21, Spring Boot 3.5, Thymeleaf |
| Database | Google Cloud Firestore (Firebase Spark plan) through the Firebase Admin Java SDK |
| Report photos | Cloudinary (free plan) |
| Security | Spring Security, BCrypt, CSRF protection |
| Email | Spring Boot Starter Mail with Brevo SMTP (free plan) |
| News bot | Jsoup |
| Tests | JUnit 5, Mockito, JaCoCo, Playwright |
| Hosting | Docker on Render (free web service) |

Everything used is open source or on a free plan that needs no credit card.

## Team

| Member | Role | Responsible for |
|---|---|---|
| Ythan Joshua Costelo | Project Manager / Frontend | Public dashboard, 4-column grid, image placeholders, shared styles, error page |
| Reuel Matthew Arcilla | Developer / QA Lead | Report form and validation, report controller and service, all tests, sample data |
| Felix Joshua H. Abing | Backend / News Bot / Database Owner | Firestore, photo storage, admin pages and password recovery, news bot, deployment |

Every source file names its owner in its header.

## Setup on macOS with VS Code

### 1. Install the tools

Install [Homebrew](https://brew.sh) first if you do not have it. Skip anything you already have.

```bash
brew install --cask temurin@21 visual-studio-code google-chrome
```

```bash
brew install maven node git
```

Google Chrome is only needed for the browser tests.

### 2. Install the VS Code extensions

In VS Code, press `Cmd+Shift+P`, run **Shell Command: Install 'code' command in PATH**, then:

```bash
code --install-extension vscjava.vscode-java-pack
```

```bash
code --install-extension vmware.vscode-boot-dev-pack
```

### 3. Get the code

```bash
git clone https://github.com/Pa-Ayo/SoftDevProject.git
```

```bash
code SoftDevProject
```

Use VS Code's own terminal for the next steps: the project settings point it to JDK 21. In another terminal, run `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` first.

### 4. Create your local settings

```bash
cp .env.example .env
```

Open `.env` and set `ADMIN_USERNAME`, `ADMIN_EMAIL` and `ADMIN_PASSWORD`. Git ignores this file. Never commit real passwords or keys.

Photos and reset emails also need the Cloudinary and Brevo values; see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). Without them the site runs, but reports with a photo are refused and reset emails are not sent.

## Run the app

### With one click in VS Code

1. Open the project folder in VS Code and wait until the Java extension has finished loading.
2. Press `Shift+Cmd+D` (Run and Debug), choose a configuration in the list at the top and press the green arrow. `F5` repeats the last one.
3. Open <http://localhost:8080>. Press the red square to stop. The emulator keeps running in VS Code's terminal panel and forgets its data when that closes.

| Configuration | Database | Needs |
|---|---|---|
| Pa-Ayo (local test database) | Local emulator, empty | Nothing; VS Code starts the emulator |
| Pa-Ayo (local, with sample reports) | Local emulator, 30 sample reports | Nothing; VS Code starts the emulator |
| Pa-Ayo (real data), switched off in `.vscode/launch.json` for now | Your Firebase project: real reports and news | `secrets/firebase-service-account.json` |

### From the terminal

Start the local database in one terminal and leave it open:

```bash
npx -y firebase-tools@15 emulators:start --only firestore --project demo-pa-ayo
```

Start the app in a second terminal:

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. The admin page is <http://localhost:8080/admin/login>. You can browse the database at <http://127.0.0.1:4000/firestore>.

To start with 30 sample reports instead of an empty database:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=seed
```

The emulator forgets its data when it stops.

## Run the tests

Unit and web tests, with the coverage gate. Nothing else needs to be running:

```bash
mvn test
```

The build fails if `ReportService`, `NewsService`, `NewsScraper` or `TextParaphraser` has under 80% line coverage. Open `target/site/jacoco/index.html` for the report.

All tests, including the database and browser tests. The emulator must be running and Google Chrome installed:

```bash
mvn verify -Pintegration
```

The combined coverage is in `target/site/jacoco-all/index.html`.

## Project structure

```
src/main/java/com/paayo/
  controller/   Web pages (routes)
  service/      Business rules
  repository/   Firestore access
  model/        Report, NewsItem, Admin and their enums
  newsbot/      Scraper, summarizer and schedule
  config/       Firebase, security, Cloudinary, first admin, sample data
  exception/    Application errors
  util/         Date conversions (Asia/Manila)
src/main/resources/
  templates/    Thymeleaf pages
  static/       Styles, scripts, images and fonts
  samples/      Invented news pages for tests and demos
src/test/       Unit, web, database and browser tests
docs/           Setup guides and the requirements checklist
```

Calls only go down: controller → service → repository.

## Administrator guide

1. Open the site, choose **Admin Login** and sign in.
2. Find a report, choose its new status and press **Update Status**. The public card changes on its next load.
3. Press **Delete** to remove a spam or unsuitable report with its photo. This cannot be undone.
4. Press **Fetch Latest News Now** to run the news bot. The page shows how many items were added.
5. Forgot your password? Choose **Forgot Password?** on the sign-in page and enter your username or registered email. The link works once, for 30 minutes.
6. Press **Logout** when you are done.

The admin page shows the newest 100 reports.

## More documents

- [Database setup](docs/DATABASE_SETUP.md)
- [Deployment on Render](docs/DEPLOYMENT.md)
- [News sources](docs/NEWS_SOURCES.md)
- [Requirements checklist](docs/REQUIREMENTS_CHECKLIST.md)
