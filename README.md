# Core Banking & Transaction Management System

Full-stack Spring Boot + React banking application with strict role-based access control (RBAC), pessimistic row locking (@Lock(LockModeType.PESSIMISTIC_WRITE)), deadlock prevention, and real-time customer activity auditing.

## Quick Start (Single Command)

    npm run dev

- React Frontend: http://localhost:5173
- Spring Boot Backend: http://localhost:8081

## Portal Credentials
- Customer Portal (Deposit, Withdraw, Transfer): customer1 / cust123
- Admin Governance Portal (Open Accounts, Freeze/Activate, Logs & System Audit): admin / admin123

## Core Architecture & Validations
- ACID Transactions & Rollbacks: Atomic @Transactional deposit, withdrawal, and inter-account transfers.
- Concurrency & Race-Condition Protection: Prevents double-spending under simultaneous withdrawals (e.g., Rs. 5,000 balance vs Rs. 3,000 + Rs. 4,000 concurrent requests).
- Deadlock Prevention: Deterministic account lock ordering across bidirectional transfers (ACC1001 <-> ACC1002).
- Audit Trail & CSV Export: Tracks customer activity logs, blocked validation attempts, and exports ledger statements to CSV.
