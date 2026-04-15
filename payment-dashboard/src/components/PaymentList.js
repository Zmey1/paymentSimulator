import React from 'react';

function PaymentList({ payments }) {
    const getStatusClass = (status) => {
        switch (status) {
            case 'APPROVED':
                return 'status-approved';
            case 'FLAGGED':
                return 'status-flagged';
            default:
                return 'status-pending';
        }
    };

    const getTypeClass = (paymentType) => {
        switch (paymentType) {
            case 'UPI':
                return 'type-upi';
            case 'CARD':
                return 'type-card';
            default:
                return 'type-transfer';
        }
    };

    const formatAmount = (amount) => new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2
    }).format(amount);

    return (
        <section className="payment-list card-panel">
            <div className="section-heading">
                <p className="section-kicker">Live Ledger</p>
                <h2>Payment History</h2>
            </div>
            {payments.length === 0 ? (
                <div className="empty-state">
                    <p>No payments yet.</p>
                    <span>New submissions will appear here as they move through the pipeline.</span>
                </div>
            ) : (
                <div className="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>ID</th>
                                <th>Sender</th>
                                <th>Receiver</th>
                                <th className="amount-cell">Amount</th>
                                <th>Type</th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                            {payments.map((payment) => (
                                <tr key={payment.id}>
                                    <td className="payment-id">{payment.id.substring(0, 8)}...</td>
                                    <td>{payment.sender}</td>
                                    <td>{payment.receiver}</td>
                                    <td className="amount-cell">{formatAmount(payment.amount)}</td>
                                    <td>
                                        <span className={`type-badge ${getTypeClass(payment.paymentType)}`}>
                                            {payment.paymentType}
                                        </span>
                                    </td>
                                    <td>
                                        <span className={`status-badge ${getStatusClass(payment.status)}`}>
                                            {payment.status}
                                        </span>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}

export default PaymentList;
