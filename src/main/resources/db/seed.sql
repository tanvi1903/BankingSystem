-- ============================================================================
-- INITIAL SEED DATA (ADMIN + CUSTOMERS + ACCOUNTS)
-- ============================================================================

INSERT IGNORE INTO customer (customer_id, full_name, email, phone, password_hash, role, kyc_status) VALUES
('CUST000', 'System Admin', 'admin@bankingsystem.com', '9000000000', 'admin123', 'ADMIN', 'VERIFIED'),
('CUST001', 'Tanvi Juneja', 'tanvi@bankingsystem.com', '9876543210', 'pass123', 'CUSTOMER', 'VERIFIED'),
('CUST002', 'Aarav Sharma', 'aarav@bankingsystem.com', '9876543211', 'pass123', 'CUSTOMER', 'VERIFIED'),
('CUST003', 'Priya Verma', 'priya@bankingsystem.com', '9876543212', 'pass123', 'CUSTOMER', 'VERIFIED'),
('CUST004', 'Rohan Mehta', 'rohan@bankingsystem.com', '9876543213', 'pass123', 'CUSTOMER', 'PENDING');

INSERT IGNORE INTO account (account_id, customer_id, account_type, balance, status, version) VALUES
('ACC1001', 'CUST001', 'SAVINGS', 10000.0000, 'ACTIVE', 0),
('ACC1002', 'CUST001', 'CURRENT', 50000.0000, 'ACTIVE', 0),
('ACC2004', 'CUST002', 'SAVINGS', 25000.0000, 'ACTIVE', 0),
('ACC3001', 'CUST003', 'SAVINGS', 15000.0000, 'ACTIVE', 0),
('ACC4001', 'CUST004', 'SAVINGS', 5000.0000, 'FROZEN', 0);