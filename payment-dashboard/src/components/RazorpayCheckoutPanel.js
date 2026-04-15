import React, { useEffect, useState } from 'react';
import {
    createRazorpayOrder,
    getRazorpayConfig,
    verifyRazorpayPayment
} from '../services/api';

const RAZORPAY_SCRIPT_SRC = 'https://checkout.razorpay.com/v1/checkout.js';

function RazorpayCheckoutPanel({ onPaymentCreated }) {
    const [config, setConfig] = useState({
        enabled: false,
        keyId: '',
        merchantName: 'PayFlow Demo',
        description: 'Sandbox Checkout',
        receiverName: 'Demo Merchant'
    });
    const [form, setForm] = useState({
        customerName: '',
        email: '',
        contact: '',
        amount: ''
    });
    const [loadingConfig, setLoadingConfig] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [message, setMessage] = useState('');

    useEffect(() => {
        const loadConfig = async () => {
            try {
                const response = await getRazorpayConfig();
                setConfig(response);
            } catch (error) {
                console.error('Failed to fetch Razorpay config:', error);
                setMessage('Could not load Razorpay test checkout configuration.');
            } finally {
                setLoadingConfig(false);
            }
        };

        loadConfig();
    }, []);

    const handleChange = (event) => {
        setForm({
            ...form,
            [event.target.name]: event.target.value
        });
    };

    const ensureCheckoutScript = () => new Promise((resolve, reject) => {
        if (window.Razorpay) {
            resolve();
            return;
        }

        const existingScript = document.querySelector(`script[src="${RAZORPAY_SCRIPT_SRC}"]`);
        if (existingScript) {
            existingScript.addEventListener('load', resolve, { once: true });
            existingScript.addEventListener('error', () => reject(new Error('Failed to load Razorpay checkout')), { once: true });
            return;
        }

        const script = document.createElement('script');
        script.src = RAZORPAY_SCRIPT_SRC;
        script.async = true;
        script.onload = resolve;
        script.onerror = () => reject(new Error('Failed to load Razorpay checkout'));
        document.body.appendChild(script);
    });

    const handleCheckout = async () => {
        const amount = parseFloat(form.amount);
        if (!form.customerName || !form.email || !form.contact || !Number.isFinite(amount) || amount <= 0) {
            setMessage('Enter customer name, email, contact, and a valid amount before opening checkout.');
            return;
        }

        setSubmitting(true);
        setMessage('');

        try {
            await ensureCheckoutScript();

            const order = await createRazorpayOrder({
                customerName: form.customerName,
                email: form.email,
                contact: form.contact,
                amount
            });

            const razorpay = new window.Razorpay({
                key: order.keyId,
                amount: order.amount,
                currency: order.currency,
                name: order.merchantName,
                description: order.description,
                order_id: order.orderId,
                prefill: {
                    name: order.customerName,
                    email: order.email,
                    contact: order.contact
                },
                theme: {
                    color: '#4fc3f7'
                },
                handler: async (response) => {
                    try {
                        await verifyRazorpayPayment({
                            razorpayOrderId: response.razorpay_order_id,
                            razorpayPaymentId: response.razorpay_payment_id,
                            razorpaySignature: response.razorpay_signature
                        });
                        setForm({
                            customerName: '',
                            email: '',
                            contact: '',
                            amount: ''
                        });
                        setMessage('Razorpay test payment accepted and sent into the simulator pipeline.');
                        if (onPaymentCreated) {
                            onPaymentCreated();
                        }
                    } catch (error) {
                        setMessage(error.response?.data?.message || 'Razorpay payment verification failed.');
                    } finally {
                        setSubmitting(false);
                    }
                },
                modal: {
                    ondismiss: () => {
                        setSubmitting(false);
                    }
                }
            });

            razorpay.open();
        } catch (error) {
            console.error('Failed to start Razorpay checkout:', error);
            setMessage(error.response?.data?.message || error.message || 'Could not start Razorpay checkout.');
            setSubmitting(false);
        }
    };

    return (
        <section className="razorpay-panel card-panel">
            <div className="section-heading">
                <p className="section-kicker">Gateway Mode</p>
                <h2>Razorpay Test Checkout</h2>
            </div>

            {loadingConfig ? (
                <p className="gateway-note">Checking Razorpay test configuration...</p>
            ) : config.enabled ? (
                <>
                    <p className="gateway-note">
                        Launch a real sandbox checkout and feed the verified result back into the same Kafka pipeline.
                    </p>

                    <div className="gateway-summary">
                        <span className="gateway-chip">Merchant: {config.merchantName}</span>
                        <span className="gateway-chip">Receiver: {config.receiverName}</span>
                    </div>

                    <div className="payment-form-grid">
                        <div className="form-group">
                            <label htmlFor="customerName">Customer Name</label>
                            <input
                                id="customerName"
                                type="text"
                                name="customerName"
                                value={form.customerName}
                                onChange={handleChange}
                                placeholder="Alice Kumar"
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="email">Email</label>
                            <input
                                id="email"
                                type="email"
                                name="email"
                                value={form.email}
                                onChange={handleChange}
                                placeholder="alice@example.com"
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="contact">Contact</label>
                            <input
                                id="contact"
                                type="tel"
                                name="contact"
                                value={form.contact}
                                onChange={handleChange}
                                placeholder="9999999999"
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="gatewayAmount">Amount</label>
                            <input
                                id="gatewayAmount"
                                type="number"
                                name="amount"
                                value={form.amount}
                                onChange={handleChange}
                                min="1"
                                step="0.01"
                                placeholder="500.00"
                                required
                            />
                        </div>
                    </div>

                    <button
                        className="gateway-button"
                        type="button"
                        disabled={submitting}
                        onClick={handleCheckout}
                    >
                        {submitting ? 'Opening Checkout...' : 'Pay With Razorpay Test'}
                    </button>
                </>
            ) : (
                <div className="gateway-disabled">
                    <p>Razorpay test checkout is currently disabled.</p>
                    <span>Set `RAZORPAY_ENABLED=true`, `RAZORPAY_KEY_ID`, and `RAZORPAY_KEY_SECRET` for local demo use.</span>
                </div>
            )}

            {message && <p className="gateway-feedback">{message}</p>}
        </section>
    );
}

export default RazorpayCheckoutPanel;
