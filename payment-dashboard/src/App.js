import React, { useState, useEffect } from 'react';
import PaymentForm from './components/PaymentForm';
import PaymentList from './components/PaymentList';
import PipelineViz from './components/PipelineViz';
import RazorpayCheckoutPanel from './components/RazorpayCheckoutPanel';
import StatsCards from './components/StatsCards';
import { getPayments, getStats } from './services/api';
import './App.css';

const EMPTY_STATS = {
    total: 0,
    approved: 0,
    flagged: 0,
    pending: 0
};

function App() {
    const [payments, setPayments] = useState([]);
    const [stats, setStats] = useState(EMPTY_STATS);

    const fetchDashboardData = async () => {
        const [paymentsResult, statsResult] = await Promise.allSettled([
            getPayments(),
            getStats()
        ]);

        if (paymentsResult.status === 'fulfilled') {
            setPayments(paymentsResult.value);
        } else {
            console.error('Failed to fetch payments:', paymentsResult.reason);
        }

        if (statsResult.status === 'fulfilled') {
            setStats({ ...EMPTY_STATS, ...statsResult.value });
        } else {
            console.error('Failed to fetch payment stats:', statsResult.reason);
        }
    };

    useEffect(() => {
        fetchDashboardData();
        const interval = setInterval(fetchDashboardData, 3000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="app-shell">
            <main className="app">
                <header className="topbar">
                    <div className="brand">
                        <div className="brand-mark">P</div>
                        <div className="brand-copy">
                            <p className="brand-name">PayFlow</p>
                            <p className="brand-subtitle">Simulator</p>
                            <h1>Payment Simulator Dashboard</h1>
                        </div>
                    </div>
                    <div className="status-strip" aria-label="system status">
                        <span className="status-pill">
                            <span className="status-dot" />
                            Kafka Connected
                        </span>
                        <span className="status-pill">
                            <span className="status-dot" />
                            PostgreSQL Online
                        </span>
                    </div>
                </header>

                <StatsCards stats={stats} />
                <PipelineViz />

                <section className="workspace-grid">
                    <div className="workspace-column">
                        <PaymentForm onPaymentCreated={fetchDashboardData} />
                        <RazorpayCheckoutPanel onPaymentCreated={fetchDashboardData} />
                    </div>
                    <PaymentList payments={payments} />
                </section>
            </main>
        </div>
    );
}

export default App;
