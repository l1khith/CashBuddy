# PaisaPal KMP — Low-Level Database Design (LLD)

## 1. Overview & Technology Stack

PaisaPal uses **SQLDelight 2.2.1** for compile-time type-safe SQLite database interactions in Kotlin Multiplatform. The SQLite database is transparently encrypted at rest with **SQLCipher 4.6.1** using **AES-256 GCM**, with the key securely generated and stored in the **Android Keystore (TEE/StrongBox)**.

Database File: `paisapal.db` (Encrypted)
Package: `com.paisapal.db`
Target Location: `shared/src/commonMain/sqldelight/com/paisapal/db/`

---

## 2. Entity Relationship Diagram (ERD)

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│   accounts      │       │  transactions   │       │   categories    │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ PK id: INTEGER  │◄──────┤ FK account_id   │       │ PK id: INTEGER  │
│    name: TEXT   │       │ PK id: INTEGER  │       │    name: TEXT   │
│    type: TEXT   │       │    amount: REAL │       │    type: TEXT   │
│    number: TEXT │       │    type: TEXT   │       │    icon: TEXT   │
│    bank: TEXT   │       │    currency:TEXT│       │    color: TEXT  │
│    balance: REAL│       │    merchant:TEXT│       │    is_default:INT│
│    currency:TEXT│       │    category_id:INT├──►  │    sort_order:INT│
│    is_active:INT│       │    account_id:INT │     │    parent_id:INT│
│    sort_order:INT│      │    source_app:TEXT│     │    created_at:INT│
│    created_at:INT│      │    raw_text:TEXT  │     └─────────────────┘
│    updated_at:INT│      │    confidence:REAL│
└─────────────────┘       │    status:TEXT    │       ┌─────────────────┐
                          │    notes: TEXT    │       │    budgets      │
                          │    timestamp:INT  │       ├─────────────────┤
                          │    created_at:INT │       │ PK id: INTEGER  │
                          │    updated_at:INT │       │ FK category_id:INT│
                          └─────────────────┘       │    amount: REAL │
                                    │               │    period: TEXT │
                                    │               │    start_date:INT│
                                    │               │    end_date: INT │
                                    │               │    is_active: INT│
                                    │               │    alert_thr:REAL│
                                    │               │    created_at: INT│
                                    │               └─────────────────┘
                                    │
┌─────────────────┐       ┌─────────┴───────┐       ┌─────────────────┐
│     goals       │       │    settings     │       │  merchant_rules │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ PK id: INTEGER  │       │ PK key: TEXT    │       │ PK id: INTEGER  │
│    name: TEXT   │       │    value: TEXT  │       │    pattern: TEXT│
│    target: REAL │       │    updated_at:INT│      │ FK category_id:INT│
│    current: REAL│       └─────────────────┘       │    is_regex: INT │
│    deadline: INT│                                 │    priority: INT │
│    category_id:INT│                               │    match_cnt:INT │
│    icon: TEXT   │                                 │    created_at:INT│
│    color: TEXT  │                                 └─────────────────┘
│    status: TEXT │
│    created_at:INT│
│    updated_at:INT│
└─────────────────┘
```

---

## 3. SQLDelight Table Definitions & Queries

### 3.1 `accounts.sq`
```sql
import kotlin.Boolean;
import kotlin.Long;

CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    type TEXT NOT NULL, -- 'BANK', 'WALLET', 'CASH', 'CREDIT_CARD', 'INVESTMENT'
    number TEXT, -- last 4 digits or account identifier
    bank TEXT,
    balance REAL NOT NULL DEFAULT 0.0,
    currency TEXT NOT NULL DEFAULT 'INR',
    is_active INTEGER AS Boolean NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER AS Long NOT NULL,
    updated_at INTEGER AS Long NOT NULL
);

CREATE INDEX idx_accounts_active ON accounts(is_active);
CREATE INDEX idx_accounts_type ON accounts(type);

getAll:
SELECT * FROM accounts ORDER BY sort_order, created_at DESC;

getById:
SELECT * FROM accounts WHERE id = ?;

getByType:
SELECT * FROM accounts WHERE type = ? AND is_active = 1 ORDER BY sort_order;

insert:
INSERT INTO accounts (name, type, number, bank, balance, currency, is_active, sort_order, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateBalance:
UPDATE accounts SET balance = ?, updated_at = ? WHERE id = ?;

update:
UPDATE accounts SET name = ?, type = ?, number = ?, bank = ?, is_active = ?, sort_order = ?, updated_at = ? WHERE id = ?;

deleteById:
DELETE FROM accounts WHERE id = ?;
```

### 3.2 `categories.sq`
```sql
import kotlin.Boolean;
import kotlin.Long;

CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    type TEXT NOT NULL, -- 'EXPENSE' or 'INCOME'
    icon TEXT NOT NULL DEFAULT 'category',
    color TEXT NOT NULL DEFAULT '#FF6B6B',
    is_default INTEGER AS Boolean NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    parent_id INTEGER,
    created_at INTEGER AS Long NOT NULL,
    FOREIGN KEY (parent_id) REFERENCES categories(id)
);

CREATE INDEX idx_categories_type ON categories(type);
CREATE INDEX idx_categories_parent ON categories(parent_id);

insertDefaults:
INSERT INTO categories (name, type, icon, color, is_default, sort_order, created_at) VALUES
('Food & Dining', 'EXPENSE', 'restaurant', '#FF6B6B', 1, 1, ?),
('Transportation', 'EXPENSE', 'directions_car', '#4ECDC4', 1, 2, ?),
('Shopping', 'EXPENSE', 'shopping_bag', '#45B7D1', 1, 3, ?),
('Bills & Utilities', 'EXPENSE', 'receipt', '#96CEB4', 1, 4, ?),
('Entertainment', 'EXPENSE', 'movie', '#DDA0DD', 1, 5, ?),
('Healthcare', 'EXPENSE', 'local_hospital', '#F7DC6F', 1, 6, ?),
('Education', 'EXPENSE', 'school', '#BB8FCE', 1, 7, ?),
('Housing', 'EXPENSE', 'home', '#E74C3C', 1, 8, ?),
('Insurance', 'EXPENSE', 'shield', '#85C1E9', 1, 9, ?),
('Investments', 'EXPENSE', 'trending_up', '#82E0AA', 1, 10, ?),
('Salary', 'INCOME', 'account_balance', '#2ECC71', 1, 1, ?),
('Refund', 'INCOME', 'replay', '#F39C12', 1, 2, ?),
('Gift', 'INCOME', 'card_giftcard', '#E91E63', 1, 3, ?),
('Investment Returns', 'INCOME', 'savings', '#9B59B6', 1, 4, ?);

getAll:
SELECT * FROM categories ORDER BY type, sort_order;

getByType:
SELECT * FROM categories WHERE type = ? ORDER BY sort_order;

getById:
SELECT * FROM categories WHERE id = ?;
```

### 3.3 `transactions.sq`
```sql
import kotlin.Boolean;
import kotlin.Long;

CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    amount REAL NOT NULL,
    type TEXT NOT NULL, -- 'DEBIT' or 'CREDIT'
    currency TEXT NOT NULL DEFAULT 'INR',
    merchant TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    account_id INTEGER,
    source_app TEXT NOT NULL,
    raw_text TEXT NOT NULL,
    confidence REAL NOT NULL DEFAULT 0.0,
    status TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'CONFIRMED', 'REJECTED', 'MODIFIED'
    notes TEXT,
    timestamp INTEGER AS Long NOT NULL,
    created_at INTEGER AS Long NOT NULL,
    updated_at INTEGER AS Long NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id),
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_transactions_timestamp ON transactions(timestamp);
CREATE INDEX idx_transactions_category ON transactions(category_id);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_merchant ON transactions(merchant);

-- Balance Update Triggers
CREATE TRIGGER update_account_balance_debit
AFTER INSERT ON transactions
WHEN NEW.type = 'DEBIT' AND NEW.status = 'CONFIRMED' AND NEW.account_id IS NOT NULL
BEGIN
    UPDATE accounts SET balance = balance - NEW.amount, updated_at = NEW.created_at WHERE id = NEW.account_id;
END;

CREATE TRIGGER update_account_balance_credit
AFTER INSERT ON transactions
WHEN NEW.type = 'CREDIT' AND NEW.status = 'CONFIRMED' AND NEW.account_id IS NOT NULL
BEGIN
    UPDATE accounts SET balance = balance + NEW.amount, updated_at = NEW.created_at WHERE id = NEW.account_id;
END;

getAll:
SELECT t.*, c.name as category_name, c.color as category_color, c.icon as category_icon,
       a.name as account_name, a.type as account_type
FROM transactions t
LEFT JOIN categories c ON t.category_id = c.id
LEFT JOIN accounts a ON t.account_id = a.id
ORDER BY t.timestamp DESC;

getById:
SELECT t.*, c.name as category_name, c.color as category_color, c.icon as category_icon,
       a.name as account_name, a.type as account_type
FROM transactions t
LEFT JOIN categories c ON t.category_id = c.id
LEFT JOIN accounts a ON t.account_id = a.id
WHERE t.id = ?;

getPending:
SELECT t.*, c.name as category_name, c.color as category_color, c.icon as category_icon,
       a.name as account_name, a.type as account_type
FROM transactions t
LEFT JOIN categories c ON t.category_id = c.id
LEFT JOIN accounts a ON t.account_id = a.id
WHERE t.status = 'PENDING'
ORDER BY t.timestamp DESC;

getByDateRange:
SELECT t.*, c.name as category_name, c.color as category_color
FROM transactions t
LEFT JOIN categories c ON t.category_id = c.id
WHERE t.timestamp BETWEEN ? AND ?
ORDER BY t.timestamp DESC;

getByCategory:
SELECT t.*, c.name as category_name, c.color as category_color
FROM transactions t
LEFT JOIN categories c ON t.category_id = c.id
WHERE t.category_id = ?
ORDER BY t.timestamp DESC;

getMonthlySummary:
SELECT 
    strftime('%Y-%m', datetime(t.timestamp/1000, 'unixepoch')) as month,
    SUM(CASE WHEN t.type = 'DEBIT' THEN t.amount ELSE 0 END) as total_debit,
    SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE 0 END) as total_credit,
    COUNT(*) as transaction_count
FROM transactions t
WHERE t.status = 'CONFIRMED'
GROUP BY month
ORDER BY month DESC;

getCategoryBreakdown:
SELECT 
    c.name as category_name,
    c.color as category_color,
    c.icon as category_icon,
    SUM(t.amount) as total_amount,
    COUNT(*) as transaction_count,
    AVG(t.amount) as average_amount
FROM transactions t
JOIN categories c ON t.category_id = c.id
WHERE t.type = 'DEBIT' AND t.status = 'CONFIRMED' AND t.timestamp BETWEEN ? AND ?
GROUP BY c.id
ORDER BY total_amount DESC;

insert:
INSERT INTO transactions (amount, type, currency, merchant, category_id, account_id, source_app, raw_text, confidence, status, notes, timestamp, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateStatus:
UPDATE transactions SET status = ?, updated_at = ? WHERE id = ?;

updateTransaction:
UPDATE transactions SET amount = ?, type = ?, merchant = ?, category_id = ?, account_id = ?, notes = ?, updated_at = ? WHERE id = ?;

deleteById:
DELETE FROM transactions WHERE id = ?;

getBalance:
SELECT 
    COALESCE(SUM(CASE WHEN type = 'CREDIT' AND status = 'CONFIRMED' THEN amount ELSE 0 END), 0) -
    COALESCE(SUM(CASE WHEN type = 'DEBIT' AND status = 'CONFIRMED' THEN amount ELSE 0 END), 0)
FROM transactions;
```

### 3.4 `merchant_rules.sq`
```sql
import kotlin.Boolean;
import kotlin.Long;

CREATE TABLE merchant_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pattern TEXT NOT NULL,
    category_id INTEGER NOT NULL,
    is_regex INTEGER AS Boolean NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL DEFAULT 0,
    match_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER AS Long NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_merchant_rules_pattern ON merchant_rules(pattern);

getAll:
SELECT mr.*, c.name as category_name, c.color as category_color
FROM merchant_rules mr
JOIN categories c ON mr.category_id = c.id
ORDER BY mr.priority DESC, mr.match_count DESC;

findMatch:
SELECT mr.*, c.name as category_name, c.color as category_color
FROM merchant_rules mr
JOIN categories c ON mr.category_id = c.id
WHERE (? LIKE '%' || mr.pattern || '%' OR mr.pattern LIKE '%' || ? || '%')
AND mr.is_regex = 0
UNION ALL
SELECT mr.*, c.name as category_name, c.color as category_color
FROM merchant_rules mr
JOIN categories c ON mr.category_id = c.id
WHERE ? REGEXP mr.pattern AND mr.is_regex = 1
ORDER BY priority DESC, match_count DESC
LIMIT 1;

incrementMatchCount:
UPDATE merchant_rules SET match_count = match_count + 1 WHERE id = ?;

insert:
INSERT INTO merchant_rules (pattern, category_id, is_regex, priority, match_count, created_at)
VALUES (?, ?, ?, ?, 0, ?);
```

### 3.5 `budgets.sq`
```sql
import kotlin.Boolean;
import kotlin.Long;

CREATE TABLE budgets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id INTEGER,
    amount REAL NOT NULL,
    period TEXT NOT NULL DEFAULT 'MONTHLY', -- 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'
    start_date INTEGER AS Long NOT NULL,
    end_date INTEGER AS Long,
    is_active INTEGER AS Boolean NOT NULL DEFAULT 1,
    alert_threshold REAL NOT NULL DEFAULT 80.0, -- percentage
    created_at INTEGER AS Long NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

getActive:
SELECT b.*, c.name as category_name, c.color as category_color,
       COALESCE(SUM(t.amount), 0) as spent_amount
FROM budgets b
LEFT JOIN categories c ON b.category_id = c.id
LEFT JOIN transactions t ON b.category_id = t.category_id 
    AND t.type = 'DEBIT' 
    AND t.status = 'CONFIRMED'
    AND t.timestamp BETWEEN b.start_date AND COALESCE(b.end_date, ?)
WHERE b.is_active = 1
GROUP BY b.id;

insert:
INSERT INTO budgets (category_id, amount, period, start_date, end_date, is_active, alert_threshold, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteById:
DELETE FROM budgets WHERE id = ?;
```

### 3.6 `goals.sq`
```sql
import kotlin.Long;

CREATE TABLE goals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    target_amount REAL NOT NULL,
    current_amount REAL NOT NULL DEFAULT 0.0,
    deadline INTEGER AS Long,
    category_id INTEGER,
    icon TEXT NOT NULL DEFAULT 'flag',
    color TEXT NOT NULL DEFAULT '#2ECC71',
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'COMPLETED', 'CANCELLED'
    created_at INTEGER AS Long NOT NULL,
    updated_at INTEGER AS Long NOT NULL,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

getAll:
SELECT * FROM goals ORDER BY created_at DESC;

insert:
INSERT INTO goals (name, target_amount, current_amount, deadline, category_id, icon, color, status, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateProgress:
UPDATE goals SET current_amount = ?, status = ?, updated_at = ? WHERE id = ?;

deleteById:
DELETE FROM goals WHERE id = ?;
```

### 3.7 `settings.sq`
```sql
CREATE TABLE settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);

get:
SELECT * FROM settings WHERE key = ?;

set:
INSERT OR REPLACE INTO settings (key, value, updated_at) VALUES (?, ?, ?);
```

---

## 4. Encryption & Key Management Architecture

1. **SQLCipher Configuration**:
   - Cipher: AES-256 GCM
   - KDF Iterations: 256,000 PBKDF2
   - Page Size: 4096 bytes
   - Raw Key derivation via passphrase retrieved from hardware Keystore.
2. **Database Driver Factory**:
   - `commonMain`: `expect class DatabaseDriverFactory { fun createDriver(passphrase: ByteArray): SqlDriver }`
   - `androidMain`: `actual class DatabaseDriverFactory(private val context: Context)` wrapping `SupportFactory` or SQLCipher driver.
   - `iosMain`: `actual class DatabaseDriverFactory` wrapping `NativeSqliteDriver`.
