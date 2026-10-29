# Expense Splitter

Expense Splitter is a complete, offline application for shared cost accounting, group expense tracking, and minimal cash flow debt settlement. Built with a dual presentation architecture — a native Java 21 / JavaFX 21 desktop application and a zero-dependency Node.js web interface — it follows the calm, high-density Ledger Calm design system with full support for light and dark themes.

---

## Technical Stack

### Desktop Application (JavaFX)
- Java: OpenJDK 21 LTS
- Build Tool: Apache Maven 3.9.6 (wrapper included via mvnw.cmd / mvnw)
- UI Toolkit: JavaFX 21 (controls, fxml, graphics, base, swing)
- Styling and Theme Engine: AtlantaFX Primer base with complete custom token overrides (tokens-light.css, tokens-dark.css, components.css)
- Typography: IBM Plex Sans for interface text and IBM Plex Mono for amounts, dates, and tabular columns
- Iconography: Ikonli 12.3.1 with Material Design 2 outlined icon pack
- Controls and Validation: ControlsFX 11.2.1
- PDF Document Generation: LibrePDF OpenPDF 1.3.39
- Database: SQLite JDBC 3.45.1.0 with plain JDBC and DAO pattern (no ORM)
- Unit Testing: JUnit 5 (Jupiter 5.10.2) and AssertJ 3.25.3
- Logging: SLF4J 2.0.12 with Logback 1.5.3
- Native Packaging: OpenJDK jpackage

### Web & REST Interface (Node.js)
- Server Runtime: Node.js (v22+ LTS) using native zero-dependency HTTP and `node:sqlite`
- Client Architecture: Single-Page Application (SPA) driven by `app.js` state engine
- Styling: Tailwind CSS design tokens matching Ledger Calm specifications
- Iconography: Material Symbols Outlined
- Endpoints: REST API for state synchronization (`/api/state`), expenses, groups, members, settlements, and preferences

---

## Architecture Overview

The application follows a layered MVC / MVVM architecture with shared database compatibility across desktop and web runtimes:

```
src/main/java/com/expensesplitter/   JavaFX Desktop Core
├── controller/                      JavaFX view controllers managing user events and bindings
├── dao/ (and impl/)                 Data Access Object interfaces & SQLite JDBC implementation
├── model/                           Domain entities and records
├── service/                         Business logic, mathematical split rules, debt graph simplification
└── util/                            Database connection manager, font loader, theme manager, exporters

Web & API Interface (Node.js)
├── server.js                        Zero-dependency native Node.js HTTP server & SQLite API
├── index.html                       Single-page application with responsive Ledger Calm interface
├── app.js                           State engine, client-side routing, modals & financial calculator
└── screens_raw/                     Design component templates compiled via build_full_suite.js
```

### Financial Precision and Integrity
- Never uses double or float for currency. All monetary amounts are stored in the SQLite database as 64-bit integer minor units (paise or cents).
- Fractional rounding follows the exact remainder distribution rule: when a split does not divide evenly (such as 100.00 split among 3 members, or 0.01 split between 2 members), the fractional remainder is distributed one minor unit at a time to the earliest members so that the sum of shares always equals the total expense amount.
- Foreign keys are enforced on SQLite connections via `PRAGMA foreign_keys = ON;`.
- Background tasks (PDF and CSV export, scheduled reminder checks) run off the JavaFX Application Thread using Task and ScheduledExecutorService.

### Debt Simplification Algorithm
The debt simplification engine applies a greedy minimum cash flow algorithm:
1. Calculates net balance for every member across selected expense ledgers and recorded settlement payments.
2. Identifies maximum debtor (most negative net balance) and maximum creditor (most positive net balance).
3. Transfers `min(|debtor|, creditor)` between them, adjusting balances iteratively until all net positions reach exactly zero.
4. Compresses N-way tangled interpersonal debts into the minimum possible number of direct settlements with zero residual.

---

## Design System Tokens (Ledger Calm)

### Typography
- Body and UI: IBM Plex Sans (Regular 400, Medium 500, SemiBold 600)
- Numbers, Dates, and Monospace: IBM Plex Mono (Regular 400, Medium 500, SemiBold 600)
- Scale: 12px caption, 14px body, 16px label, 20px section heading, 28px page title.
- Sentence case used throughout. No emojis, no em-dashes, and no gradient text.

### Color Palette
- Light Canvas Theme:
  - Page ground: `#F7F7F5`
  - Card and surface: `#FFFFFF`
  - Border: `#E2E2DC`
  - Primary text: `#1A1D1F`
  - Secondary text: `#5B6166`
  - Accent: `#1B6B5A`
  - Positive balance: `#1E6B3A`
  - Negative balance: `#A62A2A`
  - Warning: `#8A5A00`
- Dark Slate Theme:
  - Page ground: `#14171A`
  - Card and surface: `#1C2024`
  - Border: `#2E343A`
  - Primary text: `#ECEEEF`
  - Secondary text: `#A8B0B6`
  - Accent: `#4FB39B`
  - Positive balance: `#5CC08A`
  - Negative balance: `#E27878`
- Accessibility: All background and foreground pairs meet WCAG AA contrast standards.
- Component dimensions: 4px strict spacing scale (4, 8, 12, 16, 24, 32, 48), 8px border radius, 36px control height, 44px table row height.

---

## Core Features

1. Dashboard:
   - Metric strip showing You are owed, You owe, and Net balance.
   - Recent activity table with instant text filtering.
   - Spending by category breakdown with visual proportion indicators.
   - Quick settlement action shortcuts.

2. Groups:
   - Create, edit, and archive expense groups with custom currencies (INR, USD, EUR, GBP).
   - Filter by status: All, Active, Settled, and Archived.
   - Member list management with initials avatar badges.

3. Group Detail:
   - Tabbed register: Expenses, Balances, and Members.
   - Real-time search and category filtering.
   - Member balance summary showing who owes whom and simplified transfers.

4. Expense Management:
   - Title, total amount, date, category, multi-payer support, and notes.
   - Split methods: Equal, Exact amounts, Percentages, and Shares.
   - Live distribution calculation with discrepancy detection blocking invalid saves.

5. Settle Up:
   - Greedy graph compression reducing complex debts into minimal bilateral transfers.
   - Support for direct settlement recording with UPI, Bank Transfer, and Cash options.
   - Instant balance updates upon recording settlements.

6. Reminders:
   - Background daemon ScheduledExecutorService running every 4 hours and on startup.
   - Desktop system notifications via ControlsFX.
   - In-app claim counters and action buttons for Send nudge, Snooze 24 hours, and Dismiss.
   - Optional SMTP email configuration in Settings.

7. Reports and Export:
   - Filter transactions by group and date range.
   - Summary cards displaying total spending, transaction count, and average expense.
   - Streaming CSV export via CsvExporter.
   - Structured PDF statement generation via OpenPDF.

8. Settings:
   - Instant live theme toggle between Light Canvas and Dark Slate.
   - Theme persistence across application restarts.
   - Default currency selection and reminder frequency configuration.
   - Account profile management: update display name, change password, delete account.
   - SQLite database file backup and restore.

9. Offline Authentication and User Accounts:
   - Real multi-account management with instant account switching, adding accounts, and signing out.
   - Passwords protected via PBKDF2 (HMAC-SHA256, 120,000 iterations, unique per-user salt).
   - Data stored in stable user home directory `~/.expensesplitter/expensesplitter.db` so data survives rebuilds.
   - Group ownership and access isolation: members only see groups they own or belong to.
   - Clean startup schema with legacy demo data migration prompt.

---

## Screen Gallery

### Light Canvas Theme
- Dashboard: `screenshots/light-dashboard.png`
- Groups: `screenshots/light-groups.png`
- Group Detail: `screenshots/light-group_detail.png`
- Add Expense Dialog: `screenshots/light-add_expense.png`
- Settle Up: `screenshots/light-settle_up.png`
- Reminders: `screenshots/light-reminders.png`
- Reports: `screenshots/light-reports.png`
- Settings: `screenshots/light-settings.png`

### Dark Slate Theme
- Dashboard: `screenshots/dark-dashboard.png`
- Groups: `screenshots/dark-groups.png`
- Group Detail: `screenshots/dark-group_detail.png`
- Add Expense Dialog: `screenshots/dark-add_expense.png`
- Settle Up: `screenshots/dark-settle_up.png`
- Reminders: `screenshots/dark-reminders.png`
- Reports: `screenshots/dark-reports.png`
- Settings: `screenshots/dark-settings.png`

---

## Keyboard Shortcuts

- Ctrl + N: Open Add Expense dialog from any screen.
- Ctrl + F: Focus global search field.
- Escape: Close any open modal dialog.
- Tab / Shift + Tab: Traverse form inputs in logical order.

---

## Setup and Running

### Prerequisites
- Java 21 JDK (such as Microsoft OpenJDK 21 or Eclipse Temurin 21) for the desktop app.
- Node.js v22+ LTS (optional, for running the web application interface).
- Windows 10/11, macOS, or Linux.

### Run the Web Interface (Node.js)
To start the zero-dependency web interface:
```bash
node server.js
```
Open `http://localhost:3000` in your web browser.

### Run Desktop App with Maven Wrapper
To run the desktop application from source:
```bash
.\mvnw.cmd javafx:run
```
On Linux or macOS:
```bash
./mvnw javafx:run
```

### Build Shaded Executable JAR
To compile, run all tests, and package a standalone executable fat JAR:
```bash
.\mvnw.cmd clean package
```
Run the generated shaded JAR directly:
```bash
java -jar target/expense-splitter-1.0.0-SNAPSHOT-all.jar
```

### Automated Screen Capture Switch
To capture all views and modal dialogs across both light and dark themes into the `screenshots/` directory:
```bash
java -jar target/expense-splitter-1.0.0-SNAPSHOT-all.jar --capture-all
```

---

## Testing

Run all unit tests via the Maven wrapper:
```bash
.\mvnw.cmd test
```

### Test Suite Coverage (26 Tests Passing)
- `SplitCalculationServiceTest` (7 tests):
  - Equal split remainder distribution (e.g. 100.00 split among 3 members: 33.34, 33.33, 33.33).
  - Micro-cents division (0.01 split between 2 members: 0.01, 0.00).
  - Exact split exact sum verification and error rejection on mismatch.
  - Percentage split validation (100% total requirement and remainder allocation).
  - Integer shares split with proportionate assignment.
- `DebtSimplificationServiceTest` (4 tests):
  - Two-person simple debt reduction.
  - Circular debt elimination (A owes B, B owes C, C owes A collapses to zero).
  - Multi-party 4-person debt graph compression.
  - Net balance zero-sum verification across all simplified transfers.
- `CyclicDebtAndBalanceTest` (4 tests):
  - Multilateral cycle resolution and complex graph balance verification.
- `PersistenceAndRollbackTest` (4 tests):
  - Database rollback on failed transaction, SQLite foreign key enforcement, and data persistence.
- `DaoCrudTest` (4 tests):
  - MemberDao CRUD against in-memory SQLite database (`jdbc:sqlite::memory:`).
  - GroupDao CRUD and membership relationship persistence.
  - ExpenseDao with payer and split persistence.
  - PaymentDao settlement insertion and retrieval.
- `BalanceServiceTest` (1 test):
  - Verification of net balances, owed amounts, and category spending calculations.
- `IkonliTest` (1 test):
  - Material Design 2 icon pack resolution and font glyph rendering.
- `UiFlowSmokeTest` (1 test):
  - End-to-end UI workflow, session management, and authentication smoke test.

---

## Packaging with jpackage

Expense Splitter can be packaged into a native Windows application with its own bundled JRE, requiring no Java runtime on the target machine.

### 1. Build Native Self-Contained App Image
Create the input directory and package the self-contained executable:
```powershell
New-Item -ItemType Directory -Force -Path package-input
Copy-Item target\expense-splitter-1.0.0-SNAPSHOT-all.jar package-input\
jpackage --type app-image `
  --input package-input `
  --name "ExpenseSplitter" `
  --main-jar expense-splitter-1.0.0-SNAPSHOT-all.jar `
  --main-class com.expensesplitter.ExpenseSplitterApp `
  --dest target\dist
```

The resulting native executable and bundled runtime will be created at:
```
target\dist\ExpenseSplitter\ExpenseSplitter.exe
```

### 2. Build Windows Installer (.msi / .exe)
On machines with the WiX Toolset v3.x installed:
```powershell
jpackage --type msi `
  --app-image target\dist\ExpenseSplitter `
  --name "ExpenseSplitter" `
  --app-version "1.0.0" `
  --vendor "ExpenseSplitter" `
  --win-dir-chooser `
  --win-menu `
  --win-shortcut `
  --dest target\installer
```

---

## Database Setup and Storage Location

The application uses an embedded SQLite database stored outside of the repository in the user's home directory:
```
~/.expensesplitter/expensesplitter.db
```
- **Zero-Configuration Startup**: When launched for the first time by a new user (either via JavaFX or `node server.js`), the directory and database file are created automatically with the full schema, constraints, and default settings applied.
- **Data Persistence**: Because the database is stored in the user profile directory, your personal financial data persists across application updates, re-builds, and clean compiles without ever being committed to source control.

---

## License and Third-Party Attribution

### License
This project is open-source and prepared for release under the **MIT License**.

### Third-Party Assets & Attribution
- **IBM Plex Sans & IBM Plex Mono Fonts**: Mike Abbink, Bold Monday, and the IBM Design team. Distributed under the **SIL Open Font License, Version 1.1** (bundled in `src/main/resources/fonts/`).
- **AtlantaFX**: Developed by mkpaz. Distributed under the **MIT License**.
- **Ikonli**: Developed by Andres Almiray. Distributed under the **Apache License 2.0**.
- **ControlsFX**: Distributed under the **BSD 3-Clause License**.
- **LibrePDF OpenPDF**: Distributed under the **LGPL and MPL**.
- **SQLite JDBC**: Developed by Taro L. Saito / Xerial. Distributed under the **Apache License 2.0**.
- **Material Symbols**: Designed by Google. Distributed under the **Apache License 2.0**.
- **Tailwind CSS**: Developed by Tailwind Labs. Distributed under the **MIT License**.
