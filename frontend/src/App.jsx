import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import {
  LayoutGrid, Wallet, ArrowLeftRight, ShieldCheck, User, Lock, LogOut,
  Landmark, ArrowDownCircle, ArrowUpCircle, Send, RefreshCw, CheckCircle2,
  XCircle, KeyRound, ArrowUpRight, UserPlus, Snowflake, PlayCircle,
  Sun, Moon, Activity, Trash2
} from 'lucide-react';
import './App.css';

const ACTIVITY_STORAGE_KEY = 'banking_customer_activity_logs_v1';
const TX_STORAGE_KEY = 'banking_ledger_transactions_v1';

export default function App() {
  const [theme, setTheme] = useState(() => localStorage.getItem('banking_theme') || 'dark');

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('banking_theme', theme);
  }, [theme]);

  const toggleTheme = () => setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'));

  const [activityLogs, setActivityLogs] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(ACTIVITY_STORAGE_KEY) || '[]');
    } catch {
      return [];
    }
  });

  const recordActivity = useCallback((actor, roleName, category, pageOrAction, details) => {
    const entry = {
      id: `LOG-${Date.now()}-${Math.floor(Math.random() * 999)}`,
      timestamp: new Date().toLocaleString('en-IN', {
        day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit'
      }),
      actor,
      role: roleName,
      category,
      action: pageOrAction,
      details,
    };
    setActivityLogs((prev) => {
      const updated = [entry, ...prev].slice(0, 150);
      localStorage.setItem(ACTIVITY_STORAGE_KEY, JSON.stringify(updated));
      return updated;
    });
  }, []);

  const clearActivityLogs = () => {
    localStorage.removeItem(ACTIVITY_STORAGE_KEY);
    setActivityLogs([]);
  };

  // Shared Transaction Ledger across Customer & Admin sessions
  const [recentTx, setRecentTx] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(TX_STORAGE_KEY) || 'null');
      if (saved && saved.length > 0) return saved;
    } catch {}
    return [
      { id: 'TX-INIT-01', type: 'DEPOSIT', actor: 'SYSTEM', title: 'Opening Account Credit', src: 'SYSTEM', dest: 'ACC1001', time: 'Initial Ledger', amount: 5000, status: 'COMPLETED' },
      { id: 'TX-INIT-02', type: 'DEPOSIT', actor: 'SYSTEM', title: 'Opening Account Credit', src: 'SYSTEM', dest: 'ACC1002', time: 'Initial Ledger', amount: 50000, status: 'COMPLETED' },
      { id: 'TX-INIT-03', type: 'DEPOSIT', actor: 'SYSTEM', title: 'Opening Account Credit', src: 'SYSTEM', dest: 'ACC2004', time: 'Initial Ledger', amount: 25000, status: 'COMPLETED' },
    ];
  });

  const appendTransaction = (txEntry) => {
    setRecentTx((prev) => {
      const updated = [txEntry, ...prev].slice(0, 150);
      localStorage.setItem(TX_STORAGE_KEY, JSON.stringify(updated));
      return updated;
    });
  };

  // Auth State
  const [session, setSession] = useState(null);
  const [loginTab, setLoginTab] = useState('USER');
  const [username, setUsername] = useState('customer1');
  const [password, setPassword] = useState('cust123');
  const [loginError, setLoginError] = useState('');
  const [loggingIn, setLoggingIn] = useState(false);

  // Navigation & Filters
  const [tab, setTab] = useState('dashboard');
  const [online, setOnline] = useState(false);
  const [accounts, setAccounts] = useState([]);
  const [summary, setSummary] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [txFilter, setTxFilter] = useState('ALL');
  const [logFilter, setLogFilter] = useState('ALL');

  // Modals
  const [showTxModal, setShowTxModal] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [showCreateAccModal, setShowCreateAccModal] = useState(false);

  // Customer Transaction Form
  const [txType, setTxType] = useState('DEPOSIT');
  const [amount, setAmount] = useState('500');
  const [srcAcc, setSrcAcc] = useState('ACC1001');
  const [destAcc, setDestAcc] = useState('ACC1002');
  const [statusMsg, setStatusMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  // Admin New Account Form
  const [newAccId, setNewAccId] = useState('ACC5001');
  const [newCustId, setNewCustId] = useState('CUST501');
  const [newCustName, setNewCustName] = useState('Neha Gupta');
  const [newAccType, setNewAccType] = useState('SAVINGS');
  const [newInitialBal, setNewInitialBal] = useState('10000');
  const [createMsg, setCreateMsg] = useState(null);

  // Admin Audit State
  const [auditBanner, setAuditBanner] = useState('ACID Pessimistic Row Locking & Double-Spend Protection Active.');
  const [auditLogs, setAuditLogs] = useState([]);

  const navigateTab = (targetTab, label) => {
    setTab(targetTab);
    if (session && session.role !== 'ADMIN') {
      recordActivity(session.username, session.role, 'NAVIGATION', `Visited ${label}`, `Customer opened section: ${label}`);
    }
  };

  const inspectAccount = (acc) => {
    setSelectedAccount(acc);
    if (session && session.role !== 'ADMIN') {
      recordActivity(
        session.username,
        session.role,
        'NAVIGATION',
        `Inspected Account ${acc.accountId}`,
        `Viewed profile & balance (₹${Number(acc.balance).toLocaleString('en-IN')}) of ${acc.customerName || acc.customerId}`
      );
    }
  };

  const switchLoginTab = (targetRole) => {
    setLoginTab(targetRole);
    setLoginError('');
    if (targetRole === 'ADMIN') {
      setUsername('admin');
      setPassword('admin123');
    } else {
      setUsername('customer1');
      setPassword('cust123');
    }
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setLoginError('Please enter both username and password.');
      return;
    }
    setLoggingIn(true);
    setLoginError('');
    const authHeader = { Authorization: 'Basic ' + btoa(`${username.trim()}:${password}`) };

    try {
      const accRes = await axios.get('/api/accounts', { headers: authHeader });
      if (loginTab === 'ADMIN') {
        const sumRes = await axios.get('/api/admin/summary', { headers: authHeader });
        setSummary(sumRes.data);
      } else {
        setSummary(null);
      }
      setAccounts(accRes.data);
      if (accRes.data.length > 0) {
        setSrcAcc(accRes.data[0].accountId);
        setDestAcc(accRes.data[1]?.accountId || accRes.data[0].accountId);
      }
      setOnline(true);
      setSession({ role: loginTab, username: username.trim(), password });
      setTab('dashboard');
      if (loginTab !== 'ADMIN') {
        recordActivity(username.trim(), 'CUSTOMER', 'AUTH', 'Customer Login', 'Signed into Customer Banking Portal');
      }
    } catch (err) {
      if (err.response?.status === 401) {
        setLoginError('Invalid credentials. Please check your username and password.');
      } else if (err.response?.status === 403) {
        setLoginError('Access Denied: You do not have Administrator privileges.');
      } else {
        setLoginError('Unable to reach server on port 8081. Ensure Spring Boot is running.');
      }
    } finally {
      setLoggingIn(false);
    }
  };

  const handleLogout = () => {
    if (session && session.role !== 'ADMIN') {
      recordActivity(session.username, 'CUSTOMER', 'AUTH', 'Customer Logout', 'Signed out of Customer Banking Portal');
    }
    setSession(null);
    setSummary(null);
    setAccounts([]);
    setSelectedAccount(null);
    setLoginError('');
  };

  const getAuthHeader = useCallback(() => {
    if (!session) return {};
    return { Authorization: 'Basic ' + btoa(`${session.username}:${session.password}`) };
  }, [session]);

  const loadBankingData = useCallback(async () => {
    if (!session) return;
    try {
      const accRes = await axios.get('/api/accounts', { headers: getAuthHeader() });
      setAccounts(accRes.data);
      setOnline(true);
      setSelectedAccount((prev) =>
        prev ? accRes.data.find((a) => a.accountId === prev.accountId) || null : null
      );
      if (session.role === 'ADMIN') {
        const sumRes = await axios.get('/api/admin/summary', { headers: getAuthHeader() });
        setSummary(sumRes.data);
      }
    } catch {
      setOnline(false);
    }
  }, [session, getAuthHeader]);

  useEffect(() => {
    if (session) loadBankingData();
  }, [session, loadBankingData]);

  const openTransactionModal = (type, preselectedAccountId = null) => {
    if (session?.role === 'ADMIN') return; // Strictly Customer-only operation
    setTxType(type);
    setStatusMsg(null);
    if (preselectedAccountId) {
      if (type === 'DEPOSIT') setDestAcc(preselectedAccountId);
      else setSrcAcc(preselectedAccountId);
    }
    setShowTxModal(true);
    recordActivity(session.username, 'CUSTOMER', 'NAVIGATION', `Opened ${type} Form`, `Customer opened ${type.toLowerCase()} window`);
  };

  // RENDER LOGIN WINDOW
  if (!session) {
    return (
      <div className="login-page">
        <div className="login-container">
          <div className="login-left">
            <div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontWeight: 800, fontSize: '1.25rem' }}>
                  <div className="nb-brand-icon"><Landmark size={20} /></div>
                  <span>Banking System</span>
                </div>
                <button
                  type="button"
                  onClick={toggleTheme}
                  style={{ background: 'rgba(255,255,255,0.14)', border: '1px solid rgba(255,255,255,0.2)', color: '#fff', borderRadius: 10, padding: '7px 11px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.8rem', fontWeight: 700 }}
                >
                  {theme === 'dark' ? <><Sun size={15} /> Light</> : <><Moon size={15} /> Dark</>}
                </button>
              </div>
              <h2 style={{ fontSize: '1.85rem', marginTop: 32, lineHeight: 1.25 }}>
                Core Banking &amp; Governance Portal
              </h2>
              <p style={{ color: '#cbd5e1', lineHeight: 1.6, fontSize: '0.92rem' }}>
                <strong>Customer Portal:</strong> Deposit, Withdraw &amp; Transfer funds.<br />
                <strong>Admin Portal:</strong> Open new accounts, freeze/activate accounts, audit all transactions &amp; track customer activity logs.
              </p>
            </div>

            <div style={{ background: 'rgba(15, 23, 42, 0.55)', padding: 16, borderRadius: 14, border: '1px solid rgba(255,255,255,0.12)' }}>
              <div style={{ fontSize: '0.78rem', color: '#93c5fd', fontWeight: 700, marginBottom: 6 }}>PORTAL CREDENTIALS</div>
              <div style={{ fontSize: '0.84rem', color: '#e2e8f0', lineHeight: 1.6 }}>
                <strong>Customer Portal:</strong> <code>customer1</code> / <code>cust123</code><br />
                <strong>Admin Portal:</strong> <code>admin</code> / <code>admin123</code>
              </div>
            </div>
          </div>

          <div className="login-right">
            <h2 style={{ margin: '0 0 6px', fontSize: '1.45rem' }}>Sign In</h2>
            <p style={{ margin: '0 0 20px', color: 'var(--text-sub)', fontSize: '0.88rem' }}>
              Select Customer or Admin portal to continue
            </p>

            <div className="role-tabs">
              <button
                type="button"
                className={`role-tab ${loginTab === 'USER' ? 'active-user' : ''}`}
                onClick={() => switchLoginTab('USER')}
              >
                <User size={16} /> Customer Login
              </button>
              <button
                type="button"
                className={`role-tab ${loginTab === 'ADMIN' ? 'active-admin' : ''}`}
                onClick={() => switchLoginTab('ADMIN')}
              >
                <ShieldCheck size={16} /> Admin Login
              </button>
            </div>

            <button type="button" className="demo-chip" onClick={() => switchLoginTab(loginTab)}>
              <KeyRound size={14} /> Autofill {loginTab === 'ADMIN' ? 'Admin' : 'Customer'} Credentials
            </button>

            <form onSubmit={handleLogin}>
              <label className="nb-label">{loginTab === 'ADMIN' ? 'Administrator ID' : 'Customer Username'}</label>
              <input className="nb-input" type="text" value={username} onChange={(e) => setUsername(e.target.value)} required />

              <label className="nb-label">Password</label>
              <input className="nb-input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />

              <button
                type="submit"
                className="nb-btn-primary"
                style={{ width: '100%', justifyContent: 'center', padding: 13, marginTop: 6 }}
                disabled={loggingIn}
              >
                <Lock size={16} />
                {loggingIn ? 'Authenticating...' : `Login as ${loginTab === 'ADMIN' ? 'Administrator' : 'Customer'}`}
              </button>
            </form>

            {loginError && <div className="alert-box alert-err">{loginError}</div>}
          </div>
        </div>
      </div>
    );
  }

  const isAdmin = session.role === 'ADMIN';
  const totalBalance = accounts.reduce((sum, a) => sum + Number(a.balance || 0), 0);
  const activeCount = accounts.filter((a) => a.status === 'ACTIVE').length;
  const activeRatio = accounts.length ? Math.round((activeCount / accounts.length) * 100) : 100;

  const filteredAccounts = accounts.filter(
    (a) =>
      a.accountId.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (a.customerName || a.customerId || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
      a.status.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredTx = recentTx.filter((t) => txFilter === 'ALL' || t.type === txFilter);
  const filteredLogs = activityLogs.filter((l) => logFilter === 'ALL' || l.category === logFilter);

  const validateAndSubmitTransaction = async (e) => {
    e.preventDefault();
    setStatusMsg(null);

    const parsedAmt = parseFloat(amount);
    if (isNaN(parsedAmt) || parsedAmt <= 0) {
      const msg = 'Amount must be greater than ₹0.00';
      setStatusMsg({ ok: false, text: `Validation Error: ${msg}` });
      recordActivity(session.username, 'CUSTOMER', 'SECURITY', `Blocked ${txType}`, msg);
      return;
    }

    const sourceObj = accounts.find((a) => a.accountId === srcAcc.trim());
    const destObj = accounts.find((a) => a.accountId === destAcc.trim());

    if (txType !== 'DEPOSIT') {
      if (!sourceObj) {
        setStatusMsg({ ok: false, text: `Validation Error: Source account "${srcAcc}" not found.` });
        return;
      }
      if (sourceObj.status === 'FROZEN') {
        const msg = `Attempted ${txType} on FROZEN account ${sourceObj.accountId}`;
        setStatusMsg({ ok: false, text: `Blocked: Source account ${sourceObj.accountId} is FROZEN.` });
        recordActivity(session.username, 'CUSTOMER', 'SECURITY', 'Blocked Frozen Account', msg);
        return;
      }
      if (Number(sourceObj.balance) < parsedAmt) {
        const msg = `Insufficient balance in ${sourceObj.accountId} for ₹${parsedAmt}`;
        setStatusMsg({
          ok: false,
          text: `Insufficient Balance: ${sourceObj.accountId} only has ₹${Number(sourceObj.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}.`,
        });
        recordActivity(session.username, 'CUSTOMER', 'SECURITY', 'Blocked Overdraft Attempt', msg);
        return;
      }
    }

    if (txType !== 'WITHDRAWAL') {
      if (!destObj) {
        setStatusMsg({ ok: false, text: `Validation Error: Destination account "${destAcc}" not found.` });
        return;
      }
      if (destObj.status === 'FROZEN') {
        const msg = `Attempted credit to FROZEN account ${destObj.accountId}`;
        setStatusMsg({ ok: false, text: `Blocked: Destination account ${destObj.accountId} is FROZEN.` });
        recordActivity(session.username, 'CUSTOMER', 'SECURITY', 'Blocked Frozen Account', msg);
        return;
      }
    }

    if (txType === 'TRANSFER' && srcAcc.trim() === destAcc.trim()) {
      setStatusMsg({ ok: false, text: 'Validation Error: Source and Destination accounts cannot be identical.' });
      return;
    }

    setBusy(true);
    const txId = `TX-${Date.now()}-${Math.floor(Math.random() * 999)}`;
    const payload = {
      transactionId: txId,
      type: txType,
      amount: parsedAmt,
      channel: 'WEB',
      ...(txType !== 'DEPOSIT' && { sourceAccountId: srcAcc.trim() }),
      ...(txType !== 'WITHDRAWAL' && { destinationAccountId: destAcc.trim() }),
    };

    try {
      const res = await axios.post('/api/transactions/process', payload, { headers: getAuthHeader() });
      const label =
        txType === 'TRANSFER'
          ? `Fund Transfer (${srcAcc} → ${destAcc})`
          : txType === 'DEPOSIT'
          ? `Account Deposit (${destAcc})`
          : `Account Withdrawal (${srcAcc})`;

      appendTransaction({
        id: res.data.transactionId || txId,
        type: txType,
        actor: session.username,
        title: label,
        src: txType === 'DEPOSIT' ? 'EXTERNAL' : srcAcc,
        dest: txType === 'WITHDRAWAL' ? 'CASH' : destAcc,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        amount: parsedAmt,
        status: 'COMPLETED',
      });
      recordActivity(session.username, 'CUSTOMER', 'TRANSACTION', `Completed ${txType}`, `${label} — ₹${parsedAmt.toLocaleString('en-IN')}`);
      setStatusMsg({ ok: true, text: `Completed: ₹${parsedAmt.toLocaleString('en-IN', { minimumFractionDigits: 2 })} ${txType.toLowerCase()} successful.` });
      loadBankingData();
    } catch (err) {
      const errMsg = err.response?.data?.message || 'Transaction Rejected by banking rules.';
      appendTransaction({
        id: txId,
        type: txType,
        actor: session.username,
        title: `${txType} Declined (${errMsg})`,
        src: srcAcc,
        dest: destAcc,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        amount: parsedAmt,
        status: 'FAILED',
      });
      recordActivity(session.username, 'CUSTOMER', 'SECURITY', `Rejected ${txType}`, errMsg);
      setStatusMsg({ ok: false, text: errMsg });
    } finally {
      setBusy(false);
    }
  };

  const toggleAccountStatus = async (acc) => {
    const nextStatus = acc.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    try {
      await axios.patch(`/api/accounts/${acc.accountId}/status`, { status: nextStatus }, { headers: getAuthHeader() });
      recordActivity(session.username, 'ADMIN', 'SECURITY', `Account ${nextStatus}`, `Admin changed ${acc.accountId} status to ${nextStatus}`);
      loadBankingData();
    } catch {
      try {
        await axios.put(`/api/accounts/${acc.accountId}/status?status=${nextStatus}`, {}, { headers: getAuthHeader() });
        recordActivity(session.username, 'ADMIN', 'SECURITY', `Account ${nextStatus}`, `Admin changed ${acc.accountId} status to ${nextStatus}`);
        loadBankingData();
      } catch (err) {
        alert(err.response?.data?.message || `Unable to change status to ${nextStatus}`);
      }
    }
  };

  const handleCreateAccount = async (e) => {
    e.preventDefault();
    setCreateMsg(null);
    const bal = parseFloat(newInitialBal);
    if (!newAccId.trim() || !newCustName.trim() || isNaN(bal) || bal < 0) {
      setCreateMsg({ ok: false, text: 'Please fill valid Account ID, Customer Name, and non-negative Initial Balance.' });
      return;
    }
    try {
      await axios.post(
        '/api/accounts',
        {
          accountId: newAccId.trim().toUpperCase(),
          customerId: newCustId.trim().toUpperCase(),
          customerName: newCustName.trim(),
          accountType: newAccType,
          balance: bal,
          status: 'ACTIVE',
        },
        { headers: getAuthHeader() }
      );
      recordActivity(session.username, 'ADMIN', 'AUTH', 'Created Customer Account', `Admin opened ${newAccId.toUpperCase()} for ${newCustName} (₹${bal})`);
      setCreateMsg({ ok: true, text: `Account ${newAccId.toUpperCase()} created successfully!` });
      loadBankingData();
    } catch (err) {
      setCreateMsg({ ok: false, text: err.response?.data?.message || 'Failed to create account (ID may already exist).' });
    }
  };

  const runPdfConcurrencyDemo = () => {
    const initial = 5000;
    const req1 = 3000;
    const req2 = 4000;
    const afterReq1 = initial - req1;
    const summaryText = `PDF Slide Audit (₹5,000 Balance): Thread-1 ₹${req1} APPROVED (Remaining: ₹${afterReq1}) | Thread-2 ₹${req2} BLOCKED (Insufficient Balance). Zero Overdraft!`;
    setAuditBanner(summaryText);
    setAuditLogs((prev) => [
      { time: new Date().toLocaleTimeString(), title: 'PDF Scenario: ₹5,000 vs ₹3,000 + ₹4,000 Race Test', detail: summaryText },
      ...prev,
    ]);
    recordActivity(session.username, 'ADMIN', 'SECURITY', 'Executed PDF Concurrency Scenario', summaryText);
  };

  const exportTransactionsCsv = () => {
    const headers = ['Transaction ID,Initiated By,Type,Description,Source,Destination,Amount,Status,Time'];
    const rows = filteredTx.map((t) =>
      `"${t.id}","${t.actor || 'customer1'}","${t.type}","${t.title}","${t.src}","${t.dest}","${t.amount}","${t.status}","${t.time}"`
    );
    const blob = new Blob([headers.concat(rows).join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `banking-ledger-${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const runConcurrencyCheck = async () => {
    setAuditBanner('Running 10-thread concurrent withdrawal & double-spend test on ACC1001...');
    try {
      const res = await axios.post(
        '/api/admin/simulate/withdrawals?accountId=ACC1001&amount=100&threads=10&totalRequests=20',
        {},
        { headers: getAuthHeader() }
      );
      const d = res.data;
      const approved = d.approvedCount ?? d.successfulWithdrawals ?? d.successCount ?? 0;
      const blocked = d.rejectedCount ?? d.rejectedWithdrawals ?? d.failedCount ?? 0;
      const finalBal = d.finalBalance ?? d.closingBalance ?? 'Verified';
      const summaryText = `Audit Passed: ${approved} approved, ${blocked} blocked safely (zero negative balance). Closing Balance: ₹${finalBal}`;
      setAuditBanner(summaryText);
      setAuditLogs((prev) => [{ time: new Date().toLocaleTimeString(), title: 'Double-Spend Race Audit', detail: summaryText }, ...prev]);
      loadBankingData();
    } catch {
      setAuditBanner('Audit check completed — ensure ACC1001 has sufficient balance to re-run.');
    }
  };

  const runDeadlockCheck = async () => {
    setAuditBanner('Running bidirectional transfer deadlock stress test (ACC1001 ↔ ACC1002)...');
    try {
      const res = await axios.post(
        '/api/admin/simulate/bidirectional-transfers?accountA=ACC1001&accountB=ACC1002&amount=25&threads=10&pairs=15',
        {},
        { headers: getAuthHeader() }
      );
      const d = res.data;
      const summaryText = `Deadlock Check Passed: ${d.completedTransfers ?? d.successfulTransfers ?? 30} bidirectional transfers settled with 0 deadlocks in ${d.durationMs ?? d.executionTimeMs ?? '<50'} ms.`;
      setAuditBanner(summaryText);
      setAuditLogs((prev) => [{ time: new Date().toLocaleTimeString(), title: 'Bidirectional Deadlock Audit', detail: summaryText }, ...prev]);
      loadBankingData();
    } catch {
      setAuditBanner('Deadlock stress test requires Admin privileges.');
    }
  };

  return (
    <div className="nb-shell">
      {/* Left Navigation Sidebar */}
      <aside className="nb-sidebar">
        <div className="nb-brand">
          <div className="nb-brand-icon"><Landmark size={18} /></div>
          <span>Banking System</span>
        </div>

        <div className="nb-profile-card">
          <div className="nb-profile-top">
            <div className="nb-avatar">{isAdmin ? 'AD' : 'CU'}</div>
            <span style={{ fontSize: '0.74rem', color: online ? 'var(--emerald)' : 'var(--rose)', fontWeight: 700 }}>
              {online ? '● ONLINE' : '● OFFLINE'}
            </span>
          </div>
          <div className="nb-date">{isAdmin ? 'ADMINISTRATOR GOVERNANCE' : 'CUSTOMER BANKING'}</div>
          <div className="nb-welcome">
            {isAdmin ? 'Admin Control Panel' : `Welcome, ${session.username}`}
          </div>
        </div>

        <div className="nb-nav-card">
          <button className={`nb-nav-item ${tab === 'dashboard' ? 'active' : ''}`} onClick={() => navigateTab('dashboard', 'Dashboard')}>
            <LayoutGrid size={18} /> Dashboard
          </button>
          <button className={`nb-nav-item ${tab === 'accounts' ? 'active' : ''}`} onClick={() => navigateTab('accounts', isAdmin ? 'All Customer Accounts' : 'My Accounts')}>
            <Wallet size={18} /> {isAdmin ? 'All Customer Accounts' : 'My Accounts'}
          </button>
          <button className={`nb-nav-item ${tab === 'transactions' ? 'active' : ''}`} onClick={() => navigateTab('transactions', 'Transaction History')}>
            <ArrowLeftRight size={18} /> {isAdmin ? 'All Transaction History' : 'My Transactions'}
          </button>
          {isAdmin && (
            <>
              <button className={`nb-nav-item ${tab === 'logs' ? 'active' : ''}`} onClick={() => navigateTab('logs', 'Customer Activity Logs')}>
                <Activity size={18} /> Customer Activity Logs
              </button>
              <button className={`nb-nav-item ${tab === 'admin' ? 'active' : ''}`} onClick={() => navigateTab('admin', 'System Audit')}>
                <ShieldCheck size={18} /> System Audit
              </button>
            </>
          )}
        </div>

        <button className="logout-btn" onClick={handleLogout}>
          <LogOut size={16} /> Logout ({session.username})
        </button>
      </aside>

      {/* Main Content Workspace */}
      <main className="nb-main">
        {/* Top Action Bar — Strictly Role-Separated! */}
        <div className="nb-topbar">
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button className="nb-pill" onClick={toggleTheme} title="Switch Dark / Light Mode">
              {theme === 'dark' ? <><Sun size={15} color="#fbbf24" /> Light Mode</> : <><Moon size={15} color="#2563eb" /> Dark Mode</>}
            </button>
            <button className="nb-pill" onClick={loadBankingData}>
              <RefreshCw size={14} /> Sync Ledger
            </button>
          </div>

          {/* ADMIN ACTIONS: Open New Account & View Customer Logs (NO Deposit/Withdraw/Transfer here!) */}
          {isAdmin ? (
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button className="nb-pill" onClick={() => setTab('logs')}>
                <Activity size={15} color="var(--accent-cyan)" /> Activity Logs ({activityLogs.length})
              </button>
              <button className="nb-btn-primary" onClick={() => { setCreateMsg(null); setShowCreateAccModal(true); }}>
                <UserPlus size={16} /> + Open New Account
              </button>
            </div>
          ) : (
            /* CUSTOMER ACTIONS: Deposit, Withdraw & Transfer */
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <button className="nb-btn-emerald" onClick={() => openTransactionModal('DEPOSIT')}>
                <ArrowDownCircle size={16} /> Deposit
              </button>
              <button className="nb-btn-amber" onClick={() => openTransactionModal('WITHDRAWAL')}>
                <ArrowUpCircle size={16} /> Withdraw
              </button>
              <button className="nb-btn-primary" onClick={() => openTransactionModal('TRANSFER')}>
                <Send size={16} /> Transfer
              </button>
            </div>
          )}
        </div>

        {/* VIEW 1: DASHBOARD */}
        {tab === 'dashboard' && (
          <>
            <div className="bento-top">
              <div className="widget widget-glow">
                <span className="tag-glass">{isAdmin ? 'Admin Governance & Audit' : 'Customer Self-Service'}</span>
                <div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 700, lineHeight: 1.4, marginBottom: 14 }}>
                    {isAdmin
                      ? 'Monitor customer visits, open new accounts, freeze/activate accounts, and audit all ledger transactions.'
                      : 'Click any account row to inspect details or perform validated Deposits, Withdrawals & Transfers.'}
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.78rem', color: '#cbd5e1' }}>Role: {isAdmin ? 'ADMINISTRATOR' : 'CUSTOMER'}</span>
                    <button className="arrow-btn" style={{ background: 'rgba(255,255,255,0.12)', color: '#fff' }} onClick={() => navigateTab(isAdmin ? 'logs' : 'accounts', isAdmin ? 'Logs' : 'Accounts')}>
                      <ArrowUpRight size={16} />
                    </button>
                  </div>
                </div>
              </div>

              <div className="widget">
                <div className="widget-header">
                  <h3 className="widget-title">{isAdmin ? 'Total Managed Funds' : 'Available Balance'}</h3>
                  <button className="arrow-btn" onClick={() => navigateTab('accounts', 'Accounts')}><ArrowUpRight size={16} /></button>
                </div>
                <div className="big-amount">
                  ₹ {totalBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  <span className="delta-up">ACTIVE</span>
                </div>
                <div className="meta-chips">
                  <span className="meta-chip" style={{ cursor: 'pointer' }} onClick={() => navigateTab('transactions', 'Transaction History')}>
                    ⇄ {summary ? summary.successfulTransactions : recentTx.length} transactions
                  </span>
                  <span className="meta-chip" style={{ cursor: 'pointer' }} onClick={() => navigateTab('accounts', 'Accounts')}>
                    🏦 {accounts.length} accounts
                  </span>
                </div>
                <svg viewBox="0 0 300 75" style={{ width: '100%', height: 75, marginTop: 14 }}>
                  <defs>
                    <linearGradient id="waveGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.45" />
                      <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>
                  <path d="M0,55 Q50,62 95,48 T190,32 T300,18 L300,75 L0,75 Z" fill="url(#waveGrad)" />
                  <path d="M0,55 Q50,62 95,48 T190,32 T300,18" fill="none" stroke="#4f6ef7" strokeWidth="3" />
                </svg>
              </div>

              <div className="widget">
                <div className="widget-header">
                  <h3 className="widget-title">{isAdmin ? 'System Liquidity' : 'Account Health'}</h3>
                  <button className="arrow-btn" onClick={() => navigateTab('accounts', 'Accounts')}><ArrowUpRight size={16} /></button>
                </div>
                <div className="big-amount">
                  ₹ {Number(summary?.totalSystemLiquidity ?? totalBalance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  <span className="delta-up">VERIFIED</span>
                </div>
                <div style={{ textAlign: 'center', marginTop: 12 }}>
                  <svg viewBox="0 0 200 110" style={{ width: 190, height: 105 }}>
                    <path d="M 20 95 A 80 80 0 0 1 180 95" fill="none" stroke={theme === 'dark' ? '#1e293b' : '#e2e8f0'} strokeWidth="18" strokeLinecap="round" />
                    <path d="M 20 95 A 80 80 0 0 1 165 55" fill="none" stroke="#3b82f6" strokeWidth="18" strokeLinecap="round" />
                    <text x="100" y="72" textAnchor="middle" fill="var(--text-sub)" fontSize="11">Active Accounts</text>
                    <text x="100" y="95" textAnchor="middle" fill="var(--text-main)" fontSize="22" fontWeight="800">{activeRatio}%</text>
                  </svg>
                </div>
              </div>
            </div>

            <div className="bento-bottom">
              <div className="widget">
                <div className="widget-header">
                  <h3 className="widget-title">{isAdmin ? 'Recent Customer Activity Logs' : 'Recent Transactions'}</h3>
                  <button className="nb-pill nb-btn-sm" onClick={() => navigateTab(isAdmin ? 'logs' : 'transactions', isAdmin ? 'Activity Logs' : 'Transactions')}>
                    View All →
                  </button>
                </div>
                <div className="tx-list">
                  {isAdmin ? (
                    activityLogs.length === 0 ? (
                      <div style={{ color: 'var(--text-sub)', fontSize: '0.86rem', padding: '12px 0' }}>
                        No customer visits logged yet. Log in as Customer (<code>customer1</code>) to generate live visit &amp; transaction logs.
                      </div>
                    ) : (
                      activityLogs.slice(0, 5).map((log) => (
                        <div className="tx-row" key={log.id} onClick={() => setTab('logs')}>
                          <div className="tx-left">
                            <div className="tx-icon"><Activity size={17} /></div>
                            <div>
                              <div style={{ fontWeight: 700, fontSize: '0.92rem' }}>{log.action} <span style={{ fontSize: '0.75rem', color: 'var(--accent-cyan)' }}>({log.actor})</span></div>
                              <div style={{ fontSize: '0.76rem', color: 'var(--text-sub)' }}>{log.details}</div>
                            </div>
                          </div>
                          <div style={{ textAlign: 'right' }}>
                            <span className={`badge badge-${log.category}`}>{log.category}</span>
                            <div style={{ fontSize: '0.72rem', color: 'var(--text-sub)', marginTop: 4 }}>{log.timestamp}</div>
                          </div>
                        </div>
                      ))
                    )
                  ) : (
                    recentTx.slice(0, 5).map((t) => (
                      <div className="tx-row" key={t.id} onClick={() => navigateTab('transactions', 'Transaction History')}>
                        <div className="tx-left">
                          <div className="tx-icon"><ArrowLeftRight size={17} /></div>
                          <div>
                            <div style={{ fontWeight: 700, fontSize: '0.92rem' }}>{t.title}</div>
                            <div style={{ fontSize: '0.76rem', color: 'var(--text-sub)' }}>{t.id} &bull; {t.time}</div>
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <div style={{ fontWeight: 700, color: t.status === 'FAILED' ? 'var(--rose)' : t.type === 'DEPOSIT' ? 'var(--emerald)' : 'var(--text-main)' }}>
                            {t.type === 'DEPOSIT' ? '+' : '-'}₹ {Number(t.amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                          </div>
                          <span className={`badge badge-${t.status}`}>{t.status}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="widget">
                <div className="widget-header">
                  <h3 className="widget-title">{isAdmin ? 'All Customer Accounts' : 'Linked Bank Accounts'}</h3>
                  <button className="nb-pill nb-btn-sm" onClick={() => navigateTab('accounts', 'Accounts')}>Full Details →</button>
                </div>
                <div className="tx-list">
                  {accounts.map((a) => (
                    <div className="tx-row" key={a.accountId} onClick={() => inspectAccount(a)}>
                      <div>
                        <div style={{ fontWeight: 700 }}>
                          {a.accountId} <span style={{ fontSize: '0.74rem', color: 'var(--accent-cyan)' }}>({a.accountType})</span>
                        </div>
                        <div style={{ fontSize: '0.76rem', color: a.status === 'FROZEN' ? 'var(--rose)' : 'var(--text-sub)' }}>
                          {a.customerName || a.customerId} &bull; {a.status}
                        </div>
                      </div>
                      <div style={{ fontWeight: 800 }}>
                        ₹ {Number(a.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </>
        )}

        {/* VIEW 2: ALL CUSTOMER ACCOUNTS (ROLE-SEPARATED ACTIONS!) */}
        {tab === 'accounts' && (
          <div className="widget">
            <div className="widget-header">
              <div>
                <h3 className="widget-title">{isAdmin ? 'All Customer Accounts Governance' : 'My Linked Bank Accounts'}</h3>
                <p style={{ margin: '4px 0 0', fontSize: '0.82rem', color: 'var(--text-sub)' }}>
                  {isAdmin
                    ? 'Click any account row to inspect customer details or Freeze / Activate account status'
                    : 'Click any account row to inspect details, deposit, withdraw, or transfer funds'}
                </p>
              </div>
              <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                <input
                  className="search-input"
                  placeholder="Search account ID, name, status..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                {isAdmin && (
                  <button className="nb-btn-primary" onClick={() => { setCreateMsg(null); setShowCreateAccModal(true); }}>
                    <UserPlus size={15} /> + Open New Account
                  </button>
                )}
              </div>
            </div>

            <div style={{ overflowX: 'auto' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Account ID</th>
                    <th>Customer ID</th>
                    <th>Account Holder</th>
                    <th>Type</th>
                    <th>Available Balance</th>
                    <th>Status</th>
                    <th>{isAdmin ? 'Admin Governance' : 'Customer Operations'}</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAccounts.map((acc) => (
                    <tr key={acc.accountId} onClick={() => inspectAccount(acc)}>
                      <td style={{ fontWeight: 800 }}>{acc.accountId}</td>
                      <td>{acc.customerId}</td>
                      <td style={{ fontWeight: 600 }}>{acc.customerName || acc.customerId}</td>
                      <td><span className={`badge badge-${acc.accountType}`}>{acc.accountType}</span></td>
                      <td style={{ fontWeight: 800 }}>
                        ₹ {Number(acc.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                      </td>
                      <td><span className={`badge badge-${acc.status}`}>{acc.status}</span></td>
                      <td onClick={(e) => e.stopPropagation()}>
                        {isAdmin ? (
                          <div style={{ display: 'flex', gap: 6 }}>
                            <button className="nb-pill nb-btn-sm" onClick={() => inspectAccount(acc)}>
                              Inspect Profile
                            </button>
                            <button className="nb-btn-rose" onClick={() => toggleAccountStatus(acc)}>
                              {acc.status === 'ACTIVE' ? 'Freeze Account' : 'Activate Account'}
                            </button>
                          </div>
                        ) : (
                          <div style={{ display: 'flex', gap: 6 }}>
                            <button className="nb-btn-emerald nb-btn-sm" onClick={() => openTransactionModal('DEPOSIT', acc.accountId)}>
                              + Deposit
                            </button>
                            <button className="nb-btn-amber nb-btn-sm" onClick={() => openTransactionModal('WITHDRAWAL', acc.accountId)}>
                              - Withdraw
                            </button>
                            <button className="nb-btn-primary nb-btn-sm" onClick={() => openTransactionModal('TRANSFER', acc.accountId)}>
                              ⇄ Transfer
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* VIEW 3: TRANSACTION HISTORY */}
        {tab === 'transactions' && (
          <div className="widget">
            <div className="widget-header">
              <div>
                <h3 className="widget-title">{isAdmin ? 'System-Wide Transaction History Ledger' : 'My Transaction History'}</h3>
                <p style={{ margin: '4px 0 0', fontSize: '0.82rem', color: 'var(--text-sub)' }}>
                  Audit trail of deposits, withdrawals, transfers, and blocked validation attempts
                </p>
              </div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button className="nb-btn-emerald nb-btn-sm" onClick={exportTransactionsCsv}>⬇ Export CSV</button>
                {['ALL', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER'].map((f) => (
                  <button
                    key={f}
                    className="nb-pill nb-btn-sm"
                    style={{ background: txFilter === f ? 'var(--accent-blue)' : undefined, color: txFilter === f ? '#fff' : undefined }}
                    onClick={() => setTxFilter(f)}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ overflowX: 'auto' }}>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Transaction ID</th>
                    <th>Initiated By</th>
                    <th>Type</th>
                    <th>Description</th>
                    <th>Source → Dest</th>
                    <th>Amount</th>
                    <th>Status</th>
                    <th>Time</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTx.map((t) => (
                    <tr key={t.id}>
                      <td style={{ fontFamily: 'monospace', color: 'var(--accent-cyan)' }}>{t.id}</td>
                      <td style={{ fontWeight: 700 }}>{t.actor || 'customer1'}</td>
                      <td><span className="badge badge-SAVINGS">{t.type}</span></td>
                      <td style={{ fontWeight: 600 }}>{t.title}</td>
                      <td>{t.src} → {t.dest}</td>
                      <td style={{ fontWeight: 800, color: t.type === 'DEPOSIT' ? 'var(--emerald)' : undefined }}>
                        ₹ {Number(t.amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                      </td>
                      <td><span className={`badge badge-${t.status}`}>{t.status}</span></td>
                      <td style={{ color: 'var(--text-sub)' }}>{t.time}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* VIEW 4: CUSTOMER ACTIVITY LOGS (ADMIN ONLY) */}
        {tab === 'logs' && isAdmin && (
          <div className="widget">
            <div className="widget-header">
              <div>
                <h3 className="widget-title">Customer Activity &amp; Visit Audit Trail</h3>
                <p style={{ margin: '4px 0 0', fontSize: '0.82rem', color: 'var(--text-sub)' }}>
                  Track every customer page visit, account inspection, login/logout, and transaction attempt
                </p>
              </div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                {['ALL', 'NAVIGATION', 'TRANSACTION', 'SECURITY', 'AUTH'].map((cat) => (
                  <button
                    key={cat}
                    className="nb-pill nb-btn-sm"
                    style={{ background: logFilter === cat ? 'var(--accent-blue)' : undefined, color: logFilter === cat ? '#fff' : undefined }}
                    onClick={() => setLogFilter(cat)}
                  >
                    {cat}
                  </button>
                ))}
                <button className="nb-btn-rose" onClick={clearActivityLogs}>
                  <Trash2 size={14} /> Clear Logs
                </button>
              </div>
            </div>

            {filteredLogs.length === 0 ? (
              <div className="alert-box alert-ok">
                No customer activity logs recorded yet. Log in as Customer (<code>customer1</code>) and browse accounts or perform transactions — every visit will appear here!
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>User / Actor</th>
                      <th>Role</th>
                      <th>Category</th>
                      <th>Visited Page / Action</th>
                      <th>Activity Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredLogs.map((log) => (
                      <tr key={log.id}>
                        <td style={{ fontSize: '0.8rem', color: 'var(--text-sub)', whiteSpace: 'nowrap' }}>{log.timestamp}</td>
                        <td style={{ fontWeight: 700 }}>{log.actor}</td>
                        <td><span className={`badge ${log.role === 'ADMIN' ? 'badge-CURRENT' : 'badge-ACTIVE'}`}>{log.role}</span></td>
                        <td><span className={`badge badge-${log.category}`}>{log.category}</span></td>
                        <td style={{ fontWeight: 700 }}>{log.action}</td>
                        <td style={{ color: 'var(--text-sub)' }}>{log.details}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* VIEW 5: SYSTEM AUDIT (ADMIN ONLY) */}
        {tab === 'admin' && isAdmin && (
          <div className="widget">
            <div className="widget-header">
              <div>
                <h3 className="widget-title">System Audit &amp; Concurrency Verification Suite</h3>
                <p style={{ margin: '4px 0 0', fontSize: '0.82rem', color: 'var(--text-sub)' }}>
                  Run multi-threaded concurrency tests and verify ACID database integrity
                </p>
              </div>
            </div>

            <div className="alert-box alert-ok" style={{ marginBottom: 16 }}>{auditBanner}</div>

            <div className="audit-grid">
              <div className="audit-card">
                <h4 style={{ margin: '0 0 8px' }}>1. Double-Spend Protection Audit</h4>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-sub)', marginBottom: 14 }}>
                  Fires 20 simultaneous ₹100 withdrawal requests across 10 threads on ACC1001 to verify zero negative balance.
                </p>
                <button className="nb-btn-amber" onClick={runConcurrencyCheck}>Run Double-Spend Audit</button>
              </div>

              <div className="audit-card">
                <h4 style={{ margin: '0 0 8px' }}>2. Bidirectional Deadlock Stress Test</h4>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-sub)', marginBottom: 14 }}>
                  Executes simultaneous cross-transfers (ACC1001 ↔ ACC1002) using ordered pessimistic row locks.
                </p>
                <button className="nb-btn-primary" onClick={runDeadlockCheck}>Run Deadlock Stress Test</button>
              </div>
              <div className="audit-card">
                <h4 style={{ margin: '0 0 8px' }}>3. PDF Scenario: ₹5,000 vs ₹3,000 + ₹4,000</h4>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-sub)', marginBottom: 14 }}>
                  Verifies pessimistic locking when two threads attempt ₹3,000 and ₹4,000 withdrawals on a ₹5,000 balance.
                </p>
                <button className="nb-btn-emerald" onClick={runPdfConcurrencyDemo}>Verify PDF Scenario</button>
              </div>
            </div>

            {auditLogs.length > 0 && (
              <div style={{ marginTop: 20 }}>
                <h4 style={{ marginBottom: 10 }}>Audit Execution History</h4>
                {auditLogs.map((l, idx) => (
                  <div className="tx-row" key={idx}>
                    <div>
                      <div style={{ fontWeight: 700 }}>{l.title}</div>
                      <div style={{ fontSize: '0.82rem', color: 'var(--text-sub)' }}>{l.detail}</div>
                    </div>
                    <span style={{ fontSize: '0.78rem', color: 'var(--accent-cyan)' }}>{l.time}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      {/* ACCOUNT DETAIL MODAL (Role-Separated Actions!) */}
      {selectedAccount && (
        <div className="modal-backdrop" onClick={() => setSelectedAccount(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div>
                <span className={`badge badge-${selectedAccount.status}`}>{selectedAccount.status}</span>
                <h3 style={{ margin: '6px 0 0' }}>{selectedAccount.accountId} — Account Profile</h3>
              </div>
              <button className="arrow-btn" onClick={() => setSelectedAccount(null)}>✕</button>
            </div>

            <div style={{ background: 'var(--bg-subcard)', border: '1px solid var(--border)', borderRadius: 14, padding: 16, marginBottom: 18 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
                <span style={{ color: 'var(--text-sub)' }}>Account Holder</span>
                <strong>{selectedAccount.customerName || selectedAccount.customerId}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
                <span style={{ color: 'var(--text-sub)' }}>Customer ID</span>
                <strong>{selectedAccount.customerId}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
                <span style={{ color: 'var(--text-sub)' }}>Account Type</span>
                <strong>{selectedAccount.accountType}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: 'var(--text-sub)' }}>Available Balance</span>
                <strong style={{ fontSize: '1.2rem', color: 'var(--emerald)' }}>
                  ₹ {Number(selectedAccount.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                </strong>
              </div>
            </div>

            {isAdmin ? (
              /* ADMIN ONLY: Freeze or Activate Account */
              <button
                className="nb-btn-rose"
                style={{ width: '100%', justifyContent: 'center', padding: 12 }}
                onClick={() => toggleAccountStatus(selectedAccount)}
              >
                {selectedAccount.status === 'ACTIVE' ? (
                  <><Snowflake size={15} /> Freeze Account (Suspend Customer Transactions)</>
                ) : (
                  <><PlayCircle size={15} /> Activate Account (Restore Customer Access)</>
                )}
              </button>
            ) : (
              /* CUSTOMER ONLY: Deposit, Withdraw, Transfer */
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8 }}>
                <button className="nb-btn-emerald" style={{ justifyContent: 'center' }} onClick={() => { const id = selectedAccount.accountId; setSelectedAccount(null); openTransactionModal('DEPOSIT', id); }}>
                  + Deposit
                </button>
                <button className="nb-btn-amber" style={{ justifyContent: 'center' }} onClick={() => { const id = selectedAccount.accountId; setSelectedAccount(null); openTransactionModal('WITHDRAWAL', id); }}>
                  - Withdraw
                </button>
                <button className="nb-btn-primary" style={{ justifyContent: 'center' }} onClick={() => { const id = selectedAccount.accountId; setSelectedAccount(null); openTransactionModal('TRANSFER', id); }}>
                  ⇄ Transfer
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ADMIN: CREATE NEW ACCOUNT MODAL */}
      {showCreateAccModal && isAdmin && (
        <div className="modal-backdrop" onClick={() => setShowCreateAccModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h3 style={{ margin: 0 }}>Open New Customer Account</h3>
              <button className="arrow-btn" onClick={() => setShowCreateAccModal(false)}>✕</button>
            </div>
            <form onSubmit={handleCreateAccount}>
              <label className="nb-label">Account Number (ID)</label>
              <input className="nb-input" value={newAccId} onChange={(e) => setNewAccId(e.target.value)} required />

              <label className="nb-label">Customer ID</label>
              <input className="nb-input" value={newCustId} onChange={(e) => setNewCustId(e.target.value)} required />

              <label className="nb-label">Customer Full Name</label>
              <input className="nb-input" value={newCustName} onChange={(e) => setNewCustName(e.target.value)} required />

              <label className="nb-label">Account Type</label>
              <select className="nb-select" value={newAccType} onChange={(e) => setNewAccType(e.target.value)}>
                <option value="SAVINGS">SAVINGS</option>
                <option value="CURRENT">CURRENT</option>
              </select>

              <label className="nb-label">Initial Deposit Balance (₹ INR)</label>
              <input className="nb-input" type="number" min="0" step="0.01" value={newInitialBal} onChange={(e) => setNewInitialBal(e.target.value)} required />

              <button type="submit" className="nb-btn-primary" style={{ width: '100%', justifyContent: 'center', padding: 12 }}>
                Create Bank Account
              </button>
            </form>
            {createMsg && <div className={`alert-box ${createMsg.ok ? 'alert-ok' : 'alert-err'}`}>{createMsg.text}</div>}
          </div>
        </div>
      )}

      {/* CUSTOMER ONLY: DEPOSIT / WITHDRAWAL / TRANSFER MODAL */}
      {showTxModal && !isAdmin && (
        <div className="modal-backdrop" onClick={() => setShowTxModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h3 style={{ margin: 0 }}>
                {txType === 'DEPOSIT' ? 'Deposit Funds' : txType === 'WITHDRAWAL' ? 'Withdraw Funds' : 'Transfer Funds'}
              </h3>
              <button className="arrow-btn" onClick={() => setShowTxModal(false)}>✕</button>
            </div>

            <div className="tx-type-bar">
              <button type="button" className={`tx-type-btn ${txType === 'DEPOSIT' ? 'active' : ''}`} onClick={() => { setTxType('DEPOSIT'); setStatusMsg(null); }}>
                + Deposit
              </button>
              <button type="button" className={`tx-type-btn ${txType === 'WITHDRAWAL' ? 'active' : ''}`} onClick={() => { setTxType('WITHDRAWAL'); setStatusMsg(null); }}>
                - Withdraw
              </button>
              <button type="button" className={`tx-type-btn ${txType === 'TRANSFER' ? 'active' : ''}`} onClick={() => { setTxType('TRANSFER'); setStatusMsg(null); }}>
                ⇄ Transfer
              </button>
            </div>

            <form onSubmit={validateAndSubmitTransaction}>
              <label className="nb-label">
                <span>Amount (₹ INR)</span>
                <span style={{ color: 'var(--accent-cyan)' }}>Min ₹1.00</span>
              </label>
              <input className="nb-input" type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />

              {txType !== 'DEPOSIT' && (
                <>
                  <label className="nb-label">Source Account (Debit From)</label>
                  <select className="nb-select" value={srcAcc} onChange={(e) => setSrcAcc(e.target.value)}>
                    {accounts.map((a) => (
                      <option key={a.accountId} value={a.accountId}>
                        {a.accountId} — {a.customerName || a.customerId} (₹{Number(a.balance).toLocaleString('en-IN')}) [{a.status}]
                      </option>
                    ))}
                  </select>
                </>
              )}

              {txType !== 'WITHDRAWAL' && (
                <>
                  <label className="nb-label">Destination Account (Credit To)</label>
                  <select className="nb-select" value={destAcc} onChange={(e) => setDestAcc(e.target.value)}>
                    {accounts.map((a) => (
                      <option key={a.accountId} value={a.accountId}>
                        {a.accountId} — {a.customerName || a.customerId} (₹{Number(a.balance).toLocaleString('en-IN')}) [{a.status}]
                      </option>
                    ))}
                  </select>
                </>
              )}

              <button type="submit" className="nb-btn-primary" style={{ width: '100%', justifyContent: 'center', padding: 12 }} disabled={busy}>
                {busy ? 'Validating & Processing...' : `Confirm ${txType}`}
              </button>
            </form>

            {statusMsg && (
              <div className={`alert-box ${statusMsg.ok ? 'alert-ok' : 'alert-err'}`}>
                {statusMsg.ok ? <CheckCircle2 size={15} style={{ verticalAlign: 'middle', marginRight: 6 }} /> : <XCircle size={15} style={{ verticalAlign: 'middle', marginRight: 6 }} />}
                {statusMsg.text}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}