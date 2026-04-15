import axios from 'axios';

const API_BASE = process.env.REACT_APP_API_URL || '/api';

export const createPayment = async (payment) => {
    const response = await axios.post(`${API_BASE}/payments`, payment);
    return response.data;
};

export const getPayments = async () => {
    const response = await axios.get(`${API_BASE}/payments`);
    return response.data;
};

export const getStats = async () => {
    const response = await axios.get(`${API_BASE}/payments/stats`);
    return response.data;
};

export const getRazorpayConfig = async () => {
    const response = await axios.get(`${API_BASE}/payments/razorpay/config`);
    return response.data;
};

export const createRazorpayOrder = async (payload) => {
    const response = await axios.post(`${API_BASE}/payments/razorpay/orders`, payload);
    return response.data;
};

export const verifyRazorpayPayment = async (payload) => {
    const response = await axios.post(`${API_BASE}/payments/razorpay/verify`, payload);
    return response.data;
};
