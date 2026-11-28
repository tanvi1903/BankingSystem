-- Bulk Seed 52,000+ Transactions in ~1-2 seconds using Recursive CTE (H2 / PostgreSQL compatible)
INSERT INTO transactions (account_number, type, amount, status, timestamp)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 52000
)
SELECT
    CASE MOD(n, 4)
        WHEN 0 THEN 'ACC1001'
        WHEN 1 THEN 'ACC1002'
        WHEN 2 THEN 'ACC2004'
        ELSE 'ACC3001'
    END AS account_number,
    CASE MOD(n, 3)
        WHEN 0 THEN 'DEPOSIT'
        WHEN 1 THEN 'WITHDRAWAL'
        ELSE 'TRANSFER'
    END AS type,
    ROUND(100 + MOD(n * 17, 4900), 2) AS amount,
    CASE WHEN MOD(n, 25) = 0 THEN 'FAILED' ELSE 'SUCCESS' END AS status,
    CURRENT_TIMESTAMP AS timestamp
FROM seq;

-- Indexed Volume Aggregation Benchmark Query across 50,000+ rows
SELECT account_number, COUNT(*) AS total_tx, SUM(amount) AS total_volume
FROM transactions
GROUP BY account_number
ORDER BY total_volume DESC;