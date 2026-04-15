import React, { useState } from 'react';
import { createPayment } from '../services/api';

function PaymentForm({ onPaymentCreated }) {
    const [form, setForm] = useState({
        sender: '',
        receiver: '',
        amount: '',
        paymentType: 'TRANSFER'
    });
    const [submitting, setSubmitting] = useState(false);

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        try {
            await createPayment({
                ...form,
                amount: parseFloat(form.amount)
            });
            setForm({ sender: '', receiver: '', amount: '', paymentType: 'TRANSFER' });
            if (onPaymentCreated) onPaymentCreated();
        } catch (err) {
            alert('Failed to create payment: ' + err.message);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <section className="payment-form card-panel">
            <div className="section-heading">
                <p className="section-kicker">Simulator Mode</p>
                <h2>Submit Payment</h2>
            </div>
            <form className="payment-form-grid" onSubmit={handleSubmit}>
                <div className="form-group">
                    <label htmlFor="sender">Sender</label>
                    <input
                        id="sender"
                        type="text"
                        name="sender"
                        value={form.sender}
                        onChange={handleChange}
                        placeholder="Alice"
                        required
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="receiver">Receiver</label>
                    <input
                        id="receiver"
                        type="text"
                        name="receiver"
                        value={form.receiver}
                        onChange={handleChange}
                        placeholder="Bob"
                        required
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="amount">Amount</label>
                    <input
                        id="amount"
                        type="number"
                        name="amount"
                        value={form.amount}
                        onChange={handleChange}
                        min="1"
                        step="0.01"
                        placeholder="1000.00"
                        required
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="paymentType">Payment Type</label>
                    <select id="paymentType" name="paymentType" value={form.paymentType} onChange={handleChange}>
                        <option value="TRANSFER">Transfer</option>
                        <option value="UPI">UPI</option>
                        <option value="CARD">Card</option>
                    </select>
                </div>
                <button className="submit-button" type="submit" disabled={submitting}>
                    {submitting ? 'Submitting...' : 'Submit Payment'}
                </button>
            </form>
        </section>
    );
}

export default PaymentForm;
