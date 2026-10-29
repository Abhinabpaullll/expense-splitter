# Control Wiring Audit

This document records the comprehensive audit of every interactive control across all FXML layouts and Java view controllers in the Expense Splitter application.

Audit Date: 2026-10-29
Target Rules: Zero dead buttons or unhandled controls allowed. Controls must either be fully implemented or removed/disabled with explanatory tooltips.

---

## 1. Main Application Shell (`MainLayout.fxml` / `MainController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| `navDashboardBtn` (Button) | Sidebar | Dashboard | `#onNavigateDashboard` | works | None required |
| `navGroupsBtn` (Button) | Sidebar | Groups | `#onNavigateGroups` | works | None required |
| `navRemindersBtn` (Button) | Sidebar | Reminders | `#onNavigateReminders` | works | None required |
| `navReportsBtn` (Button) | Sidebar | Reports | `#onNavigateReports` | works | None required |
| `navSettingsBtn` (Button) | Sidebar | Settings | `#onNavigateSettings` | works | None required |
| `globalSearchField` (TextField) | Top Header | Filter expenses... (Ctrl+F) | `textProperty` listener | UI only | Wire listener to filter active view register or redirect to expense search |
| `addExpenseQuickBtn` (Button) | Top Header | Add expense | `#onQuickAddExpense` | works | None required |
| `themeToggleBtn` (Button) | Top Header | Light theme / Dark theme | `#onToggleTheme` | works | None required |
| `sidebar-user-card` (HBox) | Sidebar Footer | User Profile Area | Mouse click | UI only | Convert to MenuButton / ContextMenu with Switch account, Add account, and Sign out |

---

## 2. Dashboard (`DashboardView.fxml` / `DashboardController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Header | Add expense | `#onAddExpense` | works | None required |
| `tableFilterField` (TextField) | Table Toolbar | Filter expenses... | `textProperty` listener | works | None required |
| `recentActivityTable` (TableView) | Center | Recent activity table rows | Row click / double click | UI only | Add double-click / context menu to open expense details or edit dialog |
| Button | Settlements Card | View all settlements | `#onOpenSettleUp` | works | None required |
| `settleBtn` (Button) | Settlements Card | Settle (per transfer row) | `onOpenSettleUp` lambda | works | None required |

---

## 3. Groups List (`GroupsView.fxml` / `GroupsController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Header | New group | `#onNewGroup` | works | Persist created group to SQLite database with current user ownership |
| `filterAllBtn` (Button) | Toolbar | All | `#onFilterAll` | works | None required |
| `filterActiveBtn` (Button) | Toolbar | Active | `#onFilterActive` | works | None required |
| `filterSettledBtn` (Button) | Toolbar | Settled | `#onFilterSettled` | works | None required |
| `filterArchivedBtn` (Button) | Toolbar | Archived | `#onFilterArchived` | works | None required |
| `searchGroupField` (TextField) | Toolbar | Search groups... | `textProperty` listener | works | None required |
| Button (empty state) | Empty State Box | Create a group | `#onNewGroup` | works | None required |
| `openBtn` (Button) | Group Card | Open register | Navigation lambda | works | None required |
| `settleBtn` (Button) | Group Card | Settle up | Navigation lambda | works | None required |
| Group Card Context Menu | Group Card | Edit group, Archive group, Delete group | Missing | missing handler | Add ContextMenu / actions on group cards for Edit, Archive, and Delete |
| Dialog OK Button | New Group Dialog | Create | `ButtonBar.ButtonData.OK_DONE` | works | Enforce validation and SQLite persistence |
| Dialog Cancel Button | New Group Dialog | Cancel | `ButtonType.CANCEL` | works | None required |
| `currencyBox` (ComboBox) | New Group Dialog | Currency selector | Selection | works | None required |

---

## 4. Group Detail (`GroupDetailView.fxml` / `GroupDetailController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Breadcrumb | Back to Groups | `#onBackToGroups` | works | None required |
| Button | Header | Settle up | `#onSettleUp` | works | None required |
| Button | Header | Add expense | `#onAddExpense` | works | None required |
| `tabExpensesBtn` (Button) | Tab Bar | Expenses | `#onShowExpensesTab` | works | None required |
| `tabBalancesBtn` (Button) | Tab Bar | Balances | `#onShowBalancesTab` | works | None required |
| `tabMembersBtn` (Button) | Tab Bar | Members | `#onShowMembersTab` | works | None required |
| `expenseSearchField` (TextField) | Expenses Tab | Search description... | `textProperty` listener | works | None required |
| `categoryFilterBox` (ComboBox) | Expenses Tab | Category filter | `valueProperty` listener | works | None required |
| Button | Expenses Tab | Export statement | `#onExportLedger` | works | None required |
| `expensesTable` (TableView) | Expenses Tab | Expense rows | Row click / context menu | UI only | Add context menu and double-click to Edit Expense and Delete Expense |
| Button | Members Tab | Add member | `#onAddMember` | works | Support linking existing registered account or adding plain named member |
| `removeBtn` (Button) | Members Tab | Remove (per member) | Lambda action | works | Validate that member has zero balance before removal |
| Dialog OK Button | Add Member Dialog | OK | `ButtonType.OK` | works | Enforce persistence and member association |
| Dialog Cancel Button | Add Member Dialog | Cancel | `ButtonType.CANCEL` | works | None required |

---

## 5. Add Expense Dialog (`AddExpenseDialog.fxml` / `AddExpenseController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Header | Close (X) | `#onClose` | works | None required |
| Button | Footer | Cancel | `#onClose` | works | None required |
| `saveExpenseBtn` (Button) | Footer | Save expense | `#onSaveExpense` | works | Enforce atomic transaction (insert expense, payers, splits in 1 commit) |
| `splitEqualBtn` (Button) | Split Segmented | Equal | `#onSelectEqual` | works | None required |
| `splitExactBtn` (Button) | Split Segmented | Exact | `#onSelectExact` | works | None required |
| `splitPercentBtn` (Button) | Split Segmented | Percent | `#onSelectPercent` | works | None required |
| `splitSharesBtn` (Button) | Split Segmented | Shares | `#onSelectShares` | works | None required |
| `groupComboBox` (ComboBox) | Form | Group | `valueProperty` listener | works | Filter groups visible to current user |
| `payerComboBox` (ComboBox) | Form | Paid by | Selection | works | Support selecting payer from group members |
| `categoryComboBox` (ComboBox) | Form | Category | Selection | works | None required |
| `datePicker` (DatePicker) | Form | Date | Value | works | None required |
| `amountField` (TextField) | Form | Amount | `textProperty` listener | works | Enforce positive numeric minor unit parsing |
| `titleField` (TextField) | Form | Description | Input text | works | Require non-empty title before save |
| `notesField` (TextField) | Form | Notes (Optional) | Input text | works | None required |
| `cb` (CheckBox per member) | Allocation List | Member inclusion | `selectedProperty` listener | works | None required |
| `input` (TextField per member) | Allocation List | Member share / ratio | `textProperty` listener | works | None required |

---

## 6. Settle Up (`SettleUpView.fxml` / `SettleUpController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Header | Record payment | `#onRecordPayment` | works | Persist payments and update derived balances immediately |
| `scopeGroupComboBox` (ComboBox) | Scope Card | Accounting scope | `valueProperty` listener | works | None required |
| `settleBtn` (Button) | Settlement Row | Record settle | `executeDirectSettlement` | works | Persist direct settlement to database |
| Dialog `fromBox` (ComboBox) | Record Payment Dialog | Payer (From) | Selection | works | None required |
| Dialog `toBox` (ComboBox) | Record Payment Dialog | Payee (To) | Selection | works | None required |
| Dialog `methodBox` (ComboBox) | Record Payment Dialog | Payment Method | Selection | works | None required |
| Dialog `amtField` (TextField) | Record Payment Dialog | Amount | Text input | works | Validate positive decimal amount |
| Dialog Save Button | Record Payment Dialog | Save payment | OK button data | works | Persist payment record to SQLite |
| Dialog Cancel Button | Record Payment Dialog | Cancel | `ButtonType.CANCEL` | works | None required |

---

## 7. Reminders (`RemindersView.fxml` / `RemindersController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Header | Send all reminders | `#onSendAllReminders` | works | Record reminders in database and trigger toast feedback |
| `snoozeBtn` (Button) | Reminder Row | Snooze | Lambda snooze 24h | works | Persist snooze timestamp to reminders table |
| `dismissBtn` (Button) | Reminder Row | Dismiss | Lambda dismiss | works | Persist dismissed state to reminders table |
| `nudgeBtn` (Button) | Reminder Row | Send nudge | Lambda nudge | works | Record reminder attempt and dispatch optional email |

---

## 8. Reports (`ReportsView.fxml` / `ReportsController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| Button | Header | Export CSV | `#onExportCsv` | works | Write file off UI thread and display destination toast |
| Button | Header | Export PDF | `#onExportPdf` | works | Generate PDF off UI thread and display destination toast |
| Button | Filter Toolbar | Filter | `#onApplyFilter` | works | None required |
| `reportGroupComboBox` (ComboBox) | Filter Toolbar | Scope group | Selection | works | Filter by groups accessible to current user |
| `reportStartDatePicker` (DatePicker) | Filter Toolbar | From date | Selection | works | None required |
| `reportEndDatePicker` (DatePicker) | Filter Toolbar | To date | Selection | works | None required |
| `reportTable` (TableView) | Center | Ledger register rows | Selection | works | Monospace formatting and alignment |

---

## 9. Settings (`SettingsView.fxml` / `SettingsController.java`)

| Control ID / Type | Screen / Location | Label / Description | Handler Method / Event | Current Status | Remediation Plan |
|---|---|---|---|---|---|
| `lightThemeRadio` (RadioButton) | Appearance Card | Light Canvas | `#onSelectLightTheme` | works | Persist theme preference to settings table |
| `darkThemeRadio` (RadioButton) | Appearance Card | Dark Slate | `#onSelectDarkTheme` | works | Persist theme preference to settings table |
| `defaultCurrencyBox` (ComboBox) | Currency Card | Default Currency | Missing listener | UI only | Add listener to persist default currency to settings table and update session |
| `reminderFrequencyBox` (ComboBox) | Reminders Card | Check Frequency | Missing listener | UI only | Add listener to persist frequency and reconfigure reminder scheduler |
| Button | Database Card | Backup database | `#onBackupDatabase` | works | None required |
| Button | Database Card | Restore from backup | `#onRestoreDatabase` | works | Reload all application views after database restore |
| Account Management Controls | Account Card | Change name, change password, delete account | Missing | missing handler | Add Account management section in Settings with full functionality |

---

## 10. Authentication & Account Switching Views (To Be Added in Step 2)

| View / Dialog | Controls | Description | Planned Status |
|---|---|---|---|
| AuthScreen (Sign In / Register) | Username / Email field, Password field, Confirm Password field, Sign In button, Create Account button, Switch account list | Shown when no active session exists | New implementation with PBKDF2 hashing |
| User Profile Menu | Switch account, Add account, Sign out | Located in bottom sidebar area | New implementation |
| Settings Account Management | Display Name field, Current Password field, New Password field, Update Profile button, Delete Account button | Located in Settings view | New implementation |

---

## Summary of Action Items for Fixing Controls

1. Fix `globalSearchField` in `MainLayout.fxml` to filter the active view's data or trigger live search across groups and expenses.
2. Replace static user footer in `MainLayout.fxml` with an interactive account menu (`Switch account`, `Add account`, `Sign out`).
3. Add expense table row actions (double-click to view/edit, right-click context menu for Edit and Delete) in `GroupDetailView` and `DashboardView`.
4. Add group actions on `GroupsView` cards (Edit group name, Archive group, Delete group).
5. Wire `defaultCurrencyBox` and `reminderFrequencyBox` in `SettingsController` so changes immediately persist to SQLite and take effect.
6. Add the Settings > Account management card with display name updates, password changes, and account deletion.
7. Implement user authentication screens (Create Account and Sign In) powered by PBKDF2 hashing and `SessionManager`.
