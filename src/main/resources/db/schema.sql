-- ============================================================================
-- ENTERPRISE BANKING TRANSACTION PROCESSING SYSTEM - NORMALIZED DDL
-- ============================================================================

DROP TABLE IF EXISTS failed_transaction;
DROP TABLE IF EXISTS transaction_audit;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS customer;

-- 1. CUSTOMER MASTER TABLE (Supports ADMIN & CUSTOMER roles)
CREATE TABLE customer (
    customer_id VARCHAR(20) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_customer_role CHECK (role IN ('ADMIN', 'CUSTOMER')),
    CONSTRAINT chk_kyc_status CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

-- 2. ACCOUNT TABLE (Enforces non-negative balance at DB level)
CREATE TABLE account (
    account_id VARCHAR(20) PRIMARY KEY,
    customer_id VARCHAR(20) NOT NULL,
    account_type VARCHAR(20) NOT NULL DEFAULT 'SAVINGS',
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_account_balance CHECK (balance >= 0.0000),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'FROZEN')),
    CONSTRAINT chk_account_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'CORPORATE'))
);

-- 3. TRANSACTIONS TABLE (Unique transaction_id enforces DB-level idempotency)
CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    source_account_id VARCHAR(20) NULL,
    destination_account_id VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'WEB',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_source_account FOREIGN KEY (source_account_id) REFERENCES account(account_id),
    CONSTRAINT fk_txn_dest_account FOREIGN KEY (destination_account_id) REFERENCES account(account_id),
    CONSTRAINT chk_txn_amount CHECK (amount > 0.0000),
    CONSTRAINT chk_txn_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_txn_status CHECK (status IN ('SUCCESS', 'FAILED', 'REJECTED')),
    CONSTRAINT chk_txn_channel CHECK (channel IN ('ATM', 'WEB', 'MOBILE', 'BRANCH', 'SIMULATOR'))
);

-- 4. TRANSACTION_AUDIT TABLE (Execution metrics, thread name & balance snapshots)
CREATE TABLE transaction_audit (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(50) NOT NULL,
    thread_name VARCHAR(100) NOT NULL,
    duration_ms BIGINT NOT NULL,
    before_balance DECIMAL(19, 4) NULL,
    after_balance DECIMAL(19, 4) NULL,
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_txn FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);

-- 5. FAILED_TRANSACTION TABLE (Tracks every FAILED & REJECTED transaction for investigation)
CREATE TABLE failed_transaction (
    failure_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(50) NOT NULL UNIQUE,
    error_category VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(255) NOT NULL,
    source_account_id VARCHAR(20) NULL,
    destination_account_id VARCHAR(20) NULL,
    amount DECIMAL(19, 4) NULL,
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- WORKLOAD-JUSTIFIED INDEXES (For Fast Lookups, Reports & EXPLAIN Optimization)
-- ============================================================================
CREATE INDEX idx_account_customer ON account(customer_id);
CREATE INDEX idx_txn_source_account ON transactions(source_account_id);
CREATE INDEX idx_txn_dest_account ON transactions(destination_account_id);
CREATE INDEX idx_txn_created_type ON transactions(created_at, type);
CREATE INDEX idx_txn_status ON transactions(status);
CREATE INDEX idx_failed_reason ON failed_transaction(error_category, failure_reason);
CREATE INDEX idx_audit_txn_id ON transaction_audit(transaction_id);