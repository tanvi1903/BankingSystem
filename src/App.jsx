import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { ShieldCheck, RefreshCw, ArrowRightLeft, Zap, GitCompareArrows, Rocket, Landmark, Activity } from 'lucide-react';
import './App.css';

const API_BASE = 'http://localhost:8081';

export default function App() {
  const [roleCreds, setRoleCreds] = useState('admin:admin123');
  const [summary, setSummary] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [txType, setTxType] = useState('TRANSFER');
  const [amount, setAmount] = useState('100.00');
  const [srcAcc, setSrcAcc] = useState('ACC1001');
  const [destAcc, setDestAcc] = useState('ACC1002');
  const [fillToggle, setFillToggle] = useState(true);
  const [txOutput, setTxOutput] = useState('Ready. Execute a Deposit, Withdrawal, or Transfer to inspect the ACID response...');
  const [simOutput, setSimOutput] = useState('Select a concurrency stress test above to verify deterministic lock ordering and zero ledger drift...');
  const [loading, setLoading] = useState(false);

  const authHeaders = useCallback(() => ({ Authorization: 'Basic ' + btoa(roleCreds) }), [roleCreds]);

  const refreshAll = useCallback(async () => {
    try {
      const [accRes, sumRes] = await Promise.allSettled([
        axios.get(`${API_BASE}/api/accounts`, { headers: authHeaders() }),
        axios.get(`${API_BASE}/api/admin/summary`, { headers: authHeaders() }),
      ]);
      if (accRes.status === 'fulfilled') setAccounts(accRes.value.data);
      if (sumRes.status === 'fulfilled') setSummary(sumRes.value.data);
      else if (sumRes.reason?.response?.status === 403) setSummary({ restricted: true });
    } catch (err) { console.error(err); }
  }, [authHeaders]);

  useEffect(() => { refreshAll(); }, [refreshAll]);

  const handleRowClick = (accountId) => {
    if (txType === 'DEPOSIT') setDestAcc(accountId);
    else if (txType === 'WITHDRAWAL') setSrcAcc(accountId);
    else {
      if (fillToggle) setSrcAcc(accountId);
      else setDestAcc(accountId);
      setFillToggle(!fillToggle);
    }
  };

  const handleSubmitTx = async (e) => {
    e.preventDefault();
    setLoading(true);
    setTxOutput('⏳ Acquiring pessimistic row lock & executing ACID transaction...');
    const payload = {
      transactionId: `UI-${Date.now()}-${Math.floor(Math.random() * 1000)}`,
      type: txType,
      amount: parseFloat(amount),
      channel: 'WEB',
      ...(txType !== 'DEPOSIT' && { sourceAccountId: srcAcc.trim() }),
      ...(txType !== 'WITHDRAWAL' && { destinationAccountId: destAcc.trim() }),
    };
    try {
      const res = await axios.post(`${API_BASE}/api/transactions/process`, payload, { headers: authHeaders() });
      setTxOutput(JSON.stringify(res.data, null, 2));
      refreshAll();
    } catch (err) {
      setTxOutput(JSON.stringify(err.response?.data || { error: err.message }, null, 2));
    } finally { setLoading(false); }
  };

  const runSimulation = async (endpoint) => {
    setSimOutput('⏳ Running multi-threaded stress simulation against MySQL InnoDB... please wait...');
    try {
      const res = await axios.post(`${API_BASE}${endpoint}`, {}, { headers: authHeaders() });
      setSimOutput(JSON.stringify(res.data, null, 2));
      refreshAll();
    } catch (err) {
      setSimOutput(err.response?.status === 403
        ? '403 Forbidden: Switch top-right role selector to ADMIN to run stress simulations.'
        : JSON.stringify(err.response?.data || { error: err.message }, null, 2));
    }
  };

  return (
    <div className="app-container">
      <header className="topbar">
        <div className="brand">
          <div className="brand-logo"><ShieldCheck size={24} /></div>
          <div>
            <h1>AegisCore Banking Engine</h1>
            <p>React + Spring Boot &bull; ACID Pessimistic Row Locking &bull; Deadlock-Free Ledger</p>
          </div>
        </div>
        <div className="topbar-actions">
          <span className="pill-live"><span className="dot-pulse" /> MySQL InnoDB ACID Live</span>
          <select value={roleCreds} onChange={(e) => setRoleCreds(e.target.value)} style={{ width: 'auto', padding: '8px 12px', fontSize: '0.84rem' }}>
            <option value="admin:admin123">Role: ADMIN (Full Access)</option>
            <option value="customer1:cust123">Role: CUSTOMER1 (Standard)</option>
          </select>
          <button className="btn btn-ghost" onClick={refreshAll}><RefreshCw size={15} /> Sync Ledger</button>
        </div>
      </header>

      <section className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header"><span>Total System Liquidity</span><span className="kpi-tag">ZERO DRIFT</span></div>
          <div className="kpi-value">
            {summary?.restricted ? 'ADMIN ONLY' : summary ? `₹ ${Number(summary.totalSystemLiquidity).toLocaleString('en-IN', { minimumFractionDigits: 2 })}` : '--'}
          </div>
        </div>
        <div className="kpi-card">
          <div className="kpi-header"><span>Committed Transactions</span><span className="kpi-tag" style={{ color: '#34d399' }}>SUCCESS</span></div>
          <div className="kpi-value" style={{ color: '#34d399' }}>{summary?.restricted ? '403' : summary?.successfulTransactions ?? '--'}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-header"><span>Rejected / Rolled Back</span><span className="kpi-tag" style={{ color: '#fb7185' }}>GUARDED</span></div>
          <div className="kpi-value" style={{ color: '#fb7185' }}>{summary?.restricted ? 'RBAC' : summary?.failedOrRejectedTransactions ?? '--'}</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-header"><span>Avg Execution Latency</span><span className="kpi-tag">PESSIMISTIC</span></div>
          <div className="kpi-value" style={{ color: '#38bdf8' }}>{summary?.restricted ? '--' : summary ? `${summary.averageExecutionMs} ms` : '--'}</div>
        </div>
      </section>

      <section className="main-grid">
        <div className="panel">
          <div className="panel-title">
            <h2><Landmark size={18} color="#38bdf8" /> Live Customer Accounts</h2>
            <span className="panel-hint">Click any row to autofill ID</span>
          </div>
          <table>
            <thead>
              <tr><th>Account ID</th><th>Holder</th><th>Type</th><th>Available Balance</th><th>Status</th></tr>
            </thead>
            <tbody>
              {accounts.length === 0 ? (
                <tr><td colSpan="5" style={{ color: '#94a3b8' }}>Connecting to Spring Boot backend on port 8081...</td></tr>
              ) : (
                accounts.map((a) => (
                  <tr key={a.accountId} onClick={() => handleRowClick(a.accountId)} title={`Click to fill ${a.accountId}`}>
                    <td className="acc-id">{a.accountId}</td>
                    <td>{a.customerName || a.customerId}</td>
                    <td><span className="tag-pill">{a.accountType}</span></td>
                    <td className="balance-cell">₹ {Number(a.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                    <td><span className={`badge-status badge-${a.status}`}>{a.status}</span></td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="panel">
          <div className="panel-title">
            <h2><ArrowRightLeft size={18} color="#38bdf8" /> Execute ACID Transaction</h2>
            <span className="tag-pill">Idempotent UUID</span>
          </div>
          <form onSubmit={handleSubmitTx}>
            <div className="form-grid">
              <div>
                <label>Operation Type</label>
                <select value={txType} onChange={(e) => setTxType(e.target.value)}>
                  <option value="TRANSFER">TRANSFER (P2P)</option>
                  <option value="DEPOSIT">DEPOSIT (Credit)</option>
                  <option value="WITHDRAWAL">WITHDRAWAL (Debit)</option>
                </select>
              </div>
              <div>
                <label>Amount (₹ INR)</label>
                <input type="number" step="0.01" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} required />
              </div>
            </div>
            <div className="form-grid">
              {txType !== 'DEPOSIT' && (
                <div><label>Source Account ID</label><input type="text" value={srcAcc} onChange={(e) => setSrcAcc(e.target.value)} required /></div>
              )}
              {txType !== 'WITHDRAWAL' && (
                <div><label>Destination Account ID</label><input type="text" value={destAcc} onChange={(e) => setDestAcc(e.target.value)} required /></div>
              )}
            </div>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Executing...' : 'Execute Atomic Transaction'}
            </button>
          </form>
          <div className="console-box">{txOutput}</div>
        </div>
      </section>

      <section className="panel">
        <div className="panel-title">
          <h2><Activity size={18} color="#38bdf8" /> Concurrency Stress &amp; Benchmark Simulator</h2>
          <span className="tag-pill">Admin RBAC Protected</span>
        </div>
        <div className="sim-bar">
          <button className="btn btn-amber" onClick={() => runSimulation('/api/admin/simulate/withdrawals?accountId=ACC1001&amount=100&threads=10&totalRequests=20')}>
            <Zap size={16} /> 10-Thread Double-Spend Race Test
          </button>
          <button className="btn btn-indigo" onClick={() => runSimulation('/api/admin/simulate/bidirectional-transfers?accountA=ACC1001&accountB=ACC1002&amount=25&threads=10&pairs=15')}>
            <GitCompareArrows size={16} /> Bidirectional Deadlock Stress Test
          </button>
          <button className="btn btn-emerald" onClick={() => runSimulation('/api/admin/benchmark?threads=10&totalTransactions=1000')}>
            <Rocket size={16} /> Run 1,000-Tx Mixed Workload Benchmark
          </button>
        </div>
        <div className="console-box">{simOutput}</div>
      </section>
    </div>
  );
}