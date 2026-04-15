package com.payments.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.paymentservice.config.RazorpayProperties;
import com.payments.paymentservice.dto.RazorpayCheckoutStatusResponse;
import com.payments.paymentservice.dto.RazorpayOrderRequest;
import com.payments.paymentservice.dto.RazorpayOrderResponse;
import com.payments.paymentservice.dto.RazorpayVerifyRequest;
import com.payments.paymentservice.model.Payment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RazorpayGatewayService {

    private final RazorpayProperties razorpayProperties;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, PendingRazorpayOrder> pendingOrders = new ConcurrentHashMap<>();

    public RazorpayGatewayService(
            RazorpayProperties razorpayProperties,
            PaymentService paymentService,
            ObjectMapper objectMapper
    ) {
        this.razorpayProperties = razorpayProperties;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public RazorpayCheckoutStatusResponse getCheckoutStatus() {
        boolean enabled = isConfigured();
        return new RazorpayCheckoutStatusResponse(
                enabled,
                enabled ? razorpayProperties.getKeyId() : "",
                razorpayProperties.getMerchantName(),
                razorpayProperties.getDescription(),
                razorpayProperties.getReceiverName()
        );
    }

    public RazorpayOrderResponse createOrder(RazorpayOrderRequest request) {
        assertConfigured();

        if (request == null || request.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        long amountSubunits = Math.round(request.amount() * 100);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountSubunits);
        payload.put("currency", "INR");
        payload.put("receipt", "demo_" + System.currentTimeMillis());

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("customer_name", safeValue(request.customerName(), "Customer"));
        notes.put("receiver_name", razorpayProperties.getReceiverName());
        payload.put("notes", notes);

        JsonNode orderResponse = sendRequest("POST", "/orders", payload);
        String orderId = text(orderResponse, "id");
        if (orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay did not return an order id");
        }

        PendingRazorpayOrder pendingOrder = new PendingRazorpayOrder(
                orderId,
                amountSubunits,
                text(orderResponse, "currency"),
                safeValue(request.customerName(), "Customer"),
                safeValue(request.email(), ""),
                safeValue(request.contact(), ""),
                razorpayProperties.getReceiverName()
        );
        pendingOrders.put(orderId, pendingOrder);

        return new RazorpayOrderResponse(
                razorpayProperties.getKeyId(),
                orderId,
                pendingOrder.amountSubunits(),
                pendingOrder.currency(),
                razorpayProperties.getMerchantName(),
                razorpayProperties.getDescription(),
                pendingOrder.customerName(),
                pendingOrder.email(),
                pendingOrder.contact()
        );
    }

    public Payment verifyAndCreatePayment(RazorpayVerifyRequest request) {
        assertConfigured();

        if (request == null
                || isBlank(request.razorpayOrderId())
                || isBlank(request.razorpayPaymentId())
                || isBlank(request.razorpaySignature())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Razorpay verification fields");
        }

        PendingRazorpayOrder pendingOrder = pendingOrders.get(request.razorpayOrderId());
        if (pendingOrder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or expired Razorpay order");
        }

        if (!isSignatureValid(pendingOrder.orderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay signature");
        }

        RazorpayPaymentDetails paymentDetails = fetchPaymentDetails(request.razorpayPaymentId());
        if (!Objects.equals(paymentDetails.orderId(), pendingOrder.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment does not match the order");
        }

        if (!"captured".equalsIgnoreCase(paymentDetails.status())
                && !"authorized".equalsIgnoreCase(paymentDetails.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment is not successful");
        }

        Payment payment = new Payment();
        payment.setSender(pendingOrder.customerName());
        payment.setReceiver(pendingOrder.receiverName());
        payment.setAmount(pendingOrder.amountSubunits() / 100.0);
        payment.setPaymentType(normalizeMethod(paymentDetails.method()));

        Payment createdPayment = paymentService.createPayment(payment);
        pendingOrders.remove(request.razorpayOrderId());
        return createdPayment;
    }

    private RazorpayPaymentDetails fetchPaymentDetails(String paymentId) {
        JsonNode paymentResponse = sendRequest(
                "GET",
                "/payments/" + URLEncoder.encode(paymentId, StandardCharsets.UTF_8),
                null
        );

        return new RazorpayPaymentDetails(
                text(paymentResponse, "id"),
                text(paymentResponse, "order_id"),
                text(paymentResponse, "status"),
                text(paymentResponse, "method")
        );
    }

    private JsonNode sendRequest(String method, String path, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl(path)))
                .header("Authorization", authorizationHeader())
                .header("Accept", "application/json");

        if ("POST".equalsIgnoreCase(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(writeBody(body)));
        } else {
            builder.GET();
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Razorpay request failed with status " + response.statusCode()
                );
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay request failed", exception);
        }
    }

    private boolean isSignatureValid(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayProperties.getKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String generatedSignature = bytesToHex(digest);
            return generatedSignature.equals(signature);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not verify Razorpay signature", exception);
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "RAZORPAY";
        }

        return switch (method.toLowerCase(Locale.ROOT)) {
            case "upi" -> "UPI";
            case "card" -> "CARD";
            case "bank_transfer", "netbanking", "ach" -> "TRANSFER";
            default -> method.toUpperCase(Locale.ROOT);
        };
    }

    private void assertConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay test checkout is not configured"
            );
        }
    }

    private boolean isConfigured() {
        return razorpayProperties.isEnabled()
                && !isBlank(razorpayProperties.getKeyId())
                && !isBlank(razorpayProperties.getKeySecret());
    }

    private String resolveUrl(String path) {
        String baseUrl = razorpayProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    private String authorizationHeader() {
        String credentials = razorpayProperties.getKeyId() + ":" + razorpayProperties.getKeySecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String writeBody(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not serialize Razorpay request", exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String safeValue(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record PendingRazorpayOrder(
            String orderId,
            long amountSubunits,
            String currency,
            String customerName,
            String email,
            String contact,
            String receiverName
    ) {
    }

    private record RazorpayPaymentDetails(
            String paymentId,
            String orderId,
            String status,
            String method
    ) {
    }
}
