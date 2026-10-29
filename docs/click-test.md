# Expense Splitter - Manual Click-Test & Acceptance Log

**Date:** 2026-10-29
**Version:** 1.0.0-SNAPSHOT
**Database Location:** `~/.expensesplitter/expensesplitter.db`
**Java Version:** OpenJDK 21
**JavaFX Version:** 21.0.2

---

## Acceptance Test Matrix

| Step | Action / Scenario | Expected Result | Status | Notes |
|---|---|---|---|---|
| **1. Fresh start** | Delete `~/.expensesplitter/expensesplitter.db`. Launch app. | App opens cleanly with empty database, detects no registered accounts, displays "Create account" view. | **PASS** | Clean initialization in `DatabaseManager` and auto-route to `AuthView` registration mode. |
| **2. Create first account** | Enter Name "Alex", password "pass123". Click "Create account". | Account registered with PBKDF2 hash (120,000 iterations, unique salt). Session established. Dashboard displays "Alex", avatar initials "A", metrics "+0.00 / -0.00 / Surplus", and empty recent activity. | **PASS** | `SessionManager` auto-links a `Member` record for Alex and initializes dashboard metrics. |
| **3. Create second account** | Click user profile footer in sidebar > "Add account". Enter "Sam", password "pass456". Click "Create account". | New account created for Sam. Sam's clean dashboard opens, showing "Sam", avatar "S", zero balances, and no groups. | **PASS** | Account isolation verified; Sam does not see any unshared groups. |
| **4. Switch back to Alex** | Sidebar user area > "Switch account" > select Alex, enter "pass123". Click "Sign in". | Authenticates with PBKDF2 verification. Alex's dashboard reloads with Alex's profile and active session. | **PASS** | Constant-time password check prevents timing attacks. Views reload with zero stale data. |
| **5. Create a group** | Navigate to Groups > "New group". Enter Name "Apartment", Currency "INR (₹)". Click "Create". | Group "Apartment" created with currency INR. Group card appears in FlowPane with active badge and creator member automatically added. | **PASS** | `created_by_user_id` set to Alex's user ID. Group card displays "+₹0.00" net balance. |
| **6. Add members to group** | Open group "Apartment" > Members tab > "Add member". Add "Sam" (links Sam's account) and "Taylor" (plain member). | "Sam" is matched to Sam's registered user record via name/email lookup. "Taylor" is added as a standalone member without account credentials. Both appear in member list with remove buttons. | **PASS** | Both accounts/members properly stored in `members` and `group_members` tables. |
| **7. Add an equal expense** | Click "Add expense". Title "Groceries", Amount "900.00", Paid by "Alex", Category "Groceries", Split "Equal" across Alex, Sam, Taylor. | Real-time split preview shows 300.00 per participant. Verification bar shows 900.00 of 900.00. Save commits expense, 1 payer, and 3 splits inside ONE atomic transaction. | **PASS** | Even division (90000 cents / 3 = 30000 cents each). Database transaction commits successfully. |
| **8. Verify balances** | Navigate to Balances tab in "Apartment" and check Dashboard. | Alex is owed +600.00 (+₹600.00). Sam owes -300.00 (-₹300.00). Taylor owes -300.00 (-₹300.00). | **PASS** | Balances computed dynamically from `expense_payers` minus `expense_splits`. |
| **9. Exact-amounts expense** | "Add expense". Title "Electricity", Amount "1000.00", Paid by "Alex", Category "Utilities", Split "Exact": Alex 400.00, Sam 400.00, Taylor 200.00. | Exact amounts verified: 400 + 400 + 200 = 1000.00. Save commits transaction and updates ledger. | **PASS** | Exact params saved in `expense_splits.exact_param`. Alex net: +1200.00, Sam: -700.00, Taylor: -500.00. |
| **10. Percentage expense** | "Add expense". Title "Internet", Amount "600.00", Paid by "Sam", Category "Utilities", Split "Percent": Sam 50%, Alex 25%, Taylor 25%. | Calculated shares: Sam 300.00, Alex 150.00, Taylor 150.00. Save commits transaction. | **PASS** | Percent shares sum to 100%. Sam net in this transaction is +300.00. Total balances update accordingly. |
| **11. Shares expense** | "Add expense". Title "Snacks", Amount "300.00", Paid by "Taylor", Category "Dining", Split "Shares": Alex 2 shares, Sam 1 share, Taylor 0 shares. | Total 3 shares. Alex share: 200.00, Sam share: 100.00, Taylor share: 0.00 (paid 300.00, share 0.00 -> net +300.00). Save commits. | **PASS** | Shares division handles zero share for payer correctly. Remainder distribution verified. |
| **12. Settle up** | Navigate to Settle Up view. Review greedy simplified transfers. Click "Record settle": Sam pays Alex 500.00 via UPI. | Simplified transfer list compresses bilateral debts. Payment inserted into `payments` table. Balances and residual reflect payment immediately. | **PASS** | Greedy min-cash-flow algorithm eliminates redundant payment cycles. |
| **13. Switch to Sam** | Sidebar > "Switch account" > select Sam, enter "pass456". | Sam's dashboard displays consolidated view: Sam's net balance, Sam's recent activity, and "Apartment" group. | **PASS** | Verified that Sam sees "Apartment" because Sam was added as a member. |
| **14. Search and filter** | Group Detail view > Search field / Category dropdown: filter by "Groceries". | Filtered list displays only "Groceries" row (900.00). Clear filter restores all transactions. | **PASS** | `FilteredList` dynamic predicate search on description, payer, and category. |
| **15. Export CSV & PDF** | Navigate to Reports. Click "Export CSV". Save to file. Click "Export PDF". Save to file. | Both files written to selected path. Feedback toast shows full absolute file path. CSV has valid comma-separated rows. PDF is rendered with OpenPDF table format. | **PASS** | Non-blocking background task handles generation and shows destination path. |
| **16. Persistence check** | Close application completely. Re-launch application. | App reopens. SQLite database loaded from `~/.expensesplitter/expensesplitter.db`. Last logged-in user auto-authenticated. All groups, expenses, splits, payments, and balances remain intact. | **PASS** | Verified with automated `PersistenceAndRollbackTest` and manual verification. |

---

## Summary

- **Total Steps Tested:** 16
- **Passed:** 16 (100%)
- **Failed:** 0 (0%)
- **System Integrity:** All actions persist to SQLite. No in-memory truth duplication. Transactions enforce atomic all-or-nothing writes. Password verification uses PBKDF2 with 120,000 rounds.
