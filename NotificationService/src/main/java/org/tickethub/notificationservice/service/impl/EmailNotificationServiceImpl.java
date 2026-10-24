package org.tickethub.notificationservice.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;
import org.tickethub.notificationservice.event.SeatInfo;
import org.tickethub.notificationservice.service.EmailNotificationService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${notification.from-email:noreply@tickethub.com}")
    private String fromEmail = "noreply@tickethub.com";

    public EmailNotificationServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendPaymentSuccessEmail(
            String email,
            Long bookingId,
            String paymentId,
            String amount,
            String currency) {

        BigDecimal parsedAmount = null;
        if (amount != null && !amount.isBlank()) {
            try {
                parsedAmount = new BigDecimal(amount);
            } catch (NumberFormatException ignored) {
            }
        }

        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .bookingId(bookingId)
                .razorpayPaymentId(paymentId)
                .amount(parsedAmount)
                .currency(currency)
                .paymentStatus("SUCCESS")
                .paidAt(LocalDateTime.now())
                .build();

        sendPaymentSuccessEmail(email, event, paymentId, amount);
    }

    @Override
    public void sendPaymentSuccessEmail(String email, PaymentSuccessEvent event) {
        String paymentIdStr = event.getPaymentId() != null
                ? String.valueOf(event.getPaymentId())
                : (event.getRazorpayPaymentId() != null ? event.getRazorpayPaymentId() : "N/A");
        String amountStr = event.getAmount() != null ? event.getAmount().toPlainString() : "0.00";
        sendPaymentSuccessEmail(email, event, paymentIdStr, amountStr);
    }

    private void sendPaymentSuccessEmail(
            String email,
            PaymentSuccessEvent event,
            String paymentIdDisplay,
            String amountDisplay) {

        String customerName = event.getRecipientName() != null && !event.getRecipientName().isBlank()
                ? event.getRecipientName()
                : "Customer";
        String paymentStatus = event.getPaymentStatus() != null && !event.getPaymentStatus().isBlank()
                ? event.getPaymentStatus()
                : "SUCCESS";
        String razorpayRef = event.getRazorpayPaymentId() != null && !event.getRazorpayPaymentId().isBlank()
                ? event.getRazorpayPaymentId()
                : paymentIdDisplay;
        String razorpayOrderId = event.getRazorpayOrderId() != null && !event.getRazorpayOrderId().isBlank()
                ? event.getRazorpayOrderId()
                : "N/A";
        String paymentMethod = event.getPaymentMethod() != null && !event.getPaymentMethod().isBlank()
                ? event.getPaymentMethod()
                : "UPI / ONLINE";
        String currency = event.getCurrency() != null && !event.getCurrency().isBlank()
                ? event.getCurrency()
                : "INR";
        String paidAt = event.getPaidAt() != null
                ? event.getPaidAt().toString()
                : LocalDateTime.now().toString();

        String subject = "Payment Successful - Transaction Receipt | TicketHub";
        String text = buildPaymentSuccessText(customerName, paymentStatus, paymentIdDisplay, razorpayRef, razorpayOrderId, event, paymentMethod, amountDisplay, currency, paidAt);
        String html = buildPaymentSuccessHtml(customerName, paymentStatus, paymentIdDisplay, razorpayRef, razorpayOrderId, event, paymentMethod, amountDisplay, currency, paidAt);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            if (mimeMessage != null) {
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                if (fromEmail != null && !fromEmail.isBlank()) {
                    helper.setFrom(fromEmail);
                }
                helper.setTo(email);
                helper.setSubject(subject);
                helper.setText(text, html);
                mailSender.send(mimeMessage);
                log.info("Sent rich HTML payment success email to {}", email);
                return;
            }
        } catch (Exception ex) {
            log.debug("MimeMessage delivery fallback to SimpleMailMessage: {}", ex.getMessage());
        }

        // Fallback for mock/plain test runners
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    @Override
    public void sendBookingConfirmationEmail(
            String email,
            BookingConfirmedEvent event) {

        String seats = formatSeats(event);
        String seatsHtml = formatSeatsHtml(event);

        String customerName = event.getRecipientName() == null || event.getRecipientName().isBlank()
                ? "Customer"
                : event.getRecipientName();
        String ticketStatus = event.getTicketStatus() == null || event.getTicketStatus().isBlank()
                ? "CONFIRMED"
                : event.getTicketStatus();
        String ticketNumber = event.getTicketNumber() == null || event.getTicketNumber().isBlank()
                ? "N/A"
                : event.getTicketNumber();
        String ticketId = event.getTicketId() != null ? String.valueOf(event.getTicketId()) : "N/A";
        String qrCode = event.getQrCode() == null || event.getQrCode().isBlank()
                ? "TICKETHUB|" + ticketNumber + "|BOOKING:" + event.getBookingReference()
                : event.getQrCode();
        String issuedAt = event.getIssuedAt() != null
                ? event.getIssuedAt().toString()
                : LocalDateTime.now().toString();
        String venueAddress = event.getVenueAddress() == null || event.getVenueAddress().isBlank()
                ? "N/A"
                : event.getVenueAddress();
        String endTime = event.getEndTime() != null ? event.getEndTime().toString() : "N/A";
        String paymentStatus = event.getPaymentStatus() == null || event.getPaymentStatus().isBlank()
                ? "PAID"
                : event.getPaymentStatus();
        String paymentReference = event.getPaymentReference() == null || event.getPaymentReference().isBlank()
                ? "N/A"
                : event.getPaymentReference();
        String currency = event.getCurrency() == null || event.getCurrency().isBlank()
                ? "INR"
                : event.getCurrency();

        String subject = "Booking Confirmed & Ticket Generated - TicketHub";
        String text = buildBookingConfirmedText(customerName, ticketStatus, ticketNumber, ticketId, qrCode, issuedAt, venueAddress, endTime, paymentStatus, paymentReference, currency, seats, event);
        String html = buildBookingConfirmedHtml(customerName, ticketStatus, ticketNumber, ticketId, qrCode, issuedAt, venueAddress, endTime, paymentStatus, paymentReference, currency, seatsHtml, event);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            if (mimeMessage != null) {
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                if (fromEmail != null && !fromEmail.isBlank()) {
                    helper.setFrom(fromEmail);
                }
                helper.setTo(email);
                helper.setSubject(subject);
                helper.setText(text, html);
                mailSender.send(mimeMessage);
                log.info("Sent rich HTML booking confirmation email to {} for ticket {}", email, ticketNumber);
                return;
            }
        } catch (Exception ex) {
            log.debug("MimeMessage delivery fallback to SimpleMailMessage: {}", ex.getMessage());
        }

        // Fallback for mock/plain test runners
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private String buildBookingConfirmedText(
            String customerName, String ticketStatus, String ticketNumber, String ticketId, String qrCode,
            String issuedAt, String venueAddress, String endTime, String paymentStatus, String paymentReference,
            String currency, String seats, BookingConfirmedEvent event) {

        return """
                Hi %s,

                Your TicketHub booking is CONFIRMED and your ticket has been generated!

                ==================================================
                CONFIRMED TICKET DETAILS
                ==================================================
                Ticket Status      : %s
                Ticket Number      : %s
                Ticket ID          : %s
                QR Verification    : %s
                Issued At          : %s
                --------------------------------------------------
                BOOKING & EVENT DETAILS
                --------------------------------------------------
                Booking ID         : %s
                Booking Reference  : %s
                Event              : %s
                Date               : %s
                Start Time         : %s
                End Time           : %s
                Venue              : %s
                Venue Address      : %s
                Screen             : %s
                Seats              : %s
                --------------------------------------------------
                PAYMENT SUMMARY
                --------------------------------------------------
                Amount Paid        : %s %s
                Payment Status     : %s
                Payment Reference  : %s
                ==================================================

                Please present the Ticket Number or QR Verification code at the venue entrance.
                Thank you for using TicketHub!

                Regards,
                TicketHub Team
                """.formatted(
                customerName,
                ticketStatus,
                ticketNumber,
                ticketId,
                qrCode,
                issuedAt,
                event.getBookingId() != null ? event.getBookingId() : "N/A",
                event.getBookingReference(),
                event.getEventTitle(),
                event.getShowDate(),
                event.getStartTime(),
                endTime,
                event.getVenueName(),
                venueAddress,
                event.getScreenName(),
                seats,
                event.getTotalAmount(),
                currency,
                paymentStatus,
                paymentReference
        );
    }

    private String buildBookingConfirmedHtml(
            String customerName, String ticketStatus, String ticketNumber, String ticketId, String qrCode,
            String issuedAt, String venueAddress, String endTime, String paymentStatus, String paymentReference,
            String currency, String seatsHtml, BookingConfirmedEvent event) {

        String encodedQr;
        try {
            encodedQr = URLEncoder.encode(qrCode, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            encodedQr = qrCode;
        }
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=" + encodedQr;

        String eventTitle = event.getEventTitle() != null ? event.getEventTitle() : "Cinema & Live Event";
        String venueName = event.getVenueName() != null ? event.getVenueName() : "TicketHub Partner Venue";
        String screenName = event.getScreenName() != null ? event.getScreenName() : "Screen 1";
        String showDateStr = event.getShowDate() != null ? event.getShowDate().toString() : "Today";
        String startTimeStr = event.getStartTime() != null ? event.getStartTime().toString() : "TBD";
        String endTimeStr = endTime.equals("N/A") ? "" : " - " + endTime;
        String amountStr = event.getTotalAmount() != null ? event.getTotalAmount().toPlainString() : "0.00";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Confirmed Ticket - %s</title>
                </head>
                <body style="margin:0; padding:0; background-color:#0f172a; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color:#1e293b; line-height:1.5;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0f172a; padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="width:100%%; max-width:600px; background-color:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 25px 50px -12px rgba(0,0,0,0.5); border:1px solid #334155;">
                          
                          <!-- Premium Brand Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg, #090d16 0%%, #1e1b4b 60%%, #4338ca 100%%); padding:36px 32px 30px; text-align:center;">
                              <div style="display:inline-block; padding:5px 16px; background:rgba(99,102,241,0.25); border:1px solid rgba(165,180,252,0.4); border-radius:9999px; font-size:12px; font-weight:700; color:#e0e7ff; text-transform:uppercase; letter-spacing:1.5px; margin-bottom:14px;">
                                🎟️ Official Digital Ticket
                              </div>
                              <h1 style="margin:0; color:#ffffff; font-size:28px; font-weight:900; letter-spacing:-0.5px;">
                                Ticket<span style="color:#818cf8;">Hub</span>
                              </h1>
                              <p style="margin:8px 0 0; color:#cbd5e1; font-size:14px;">
                                Congratulations %s! Your booking is confirmed.
                              </p>
                            </td>
                          </tr>

                          <!-- Status Strip -->
                          <tr>
                            <td style="background-color:#f8fafc; padding:14px 32px; border-bottom:1px solid #e2e8f0;">
                              <table role="presentation" width="100%%">
                                <tr>
                                  <td>
                                    <span style="font-size:11px; font-weight:700; color:#64748b; text-transform:uppercase; letter-spacing:0.5px;">Booking Reference</span>
                                    <div style="font-size:15px; font-weight:800; color:#0f172a; font-family:monospace; margin-top:2px;">%s</div>
                                  </td>
                                  <td align="right">
                                    <span style="display:inline-block; padding:5px 14px; background-color:#dcfce7; color:#15803d; border:1px solid #86efac; border-radius:9999px; font-size:12px; font-weight:800; text-transform:uppercase; letter-spacing:0.5px;">
                                      ● %s
                                    </span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Main Event Card -->
                          <tr>
                            <td style="padding:28px 32px 20px;">
                              <span style="font-size:11px; font-weight:800; color:#4f46e5; text-transform:uppercase; letter-spacing:1.5px;">Show / Event</span>
                              <h2 style="margin:4px 0 18px; color:#0f172a; font-size:24px; font-weight:900; line-height:1.2;">%s</h2>

                              <!-- Show Meta 2x2 Grid -->
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc; border-radius:14px; border:1px solid #e2e8f0; margin-bottom:24px;">
                                <tr>
                                  <td style="padding:14px 18px; width:50%%; border-right:1px solid #e2e8f0; border-bottom:1px solid #e2e8f0;">
                                    <div style="font-size:11px; font-weight:700; color:#64748b; text-transform:uppercase;">Date</div>
                                    <div style="font-size:14px; font-weight:800; color:#0f172a; margin-top:3px;">📅 %s</div>
                                  </td>
                                  <td style="padding:14px 18px; width:50%%; border-bottom:1px solid #e2e8f0;">
                                    <div style="font-size:11px; font-weight:700; color:#64748b; text-transform:uppercase;">Time</div>
                                    <div style="font-size:14px; font-weight:800; color:#0f172a; margin-top:3px;">⏰ %s%s</div>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:14px 18px; border-right:1px solid #e2e8f0;">
                                    <div style="font-size:11px; font-weight:700; color:#64748b; text-transform:uppercase;">Venue</div>
                                    <div style="font-size:14px; font-weight:800; color:#0f172a; margin-top:3px;">📍 %s</div>
                                    <div style="font-size:11px; color:#64748b; margin-top:2px;">%s</div>
                                  </td>
                                  <td style="padding:14px 18px;">
                                    <div style="font-size:11px; font-weight:700; color:#64748b; text-transform:uppercase;">Screen / Audi</div>
                                    <div style="font-size:14px; font-weight:800; color:#0f172a; margin-top:3px;">🎬 %s</div>
                                  </td>
                                </tr>
                              </table>

                              <!-- Reserved Seats -->
                              <div style="margin-bottom:24px;">
                                <span style="font-size:11px; font-weight:800; color:#64748b; text-transform:uppercase; letter-spacing:0.5px;">Reserved Seats</span>
                                <div style="margin-top:8px;">
                                  %s
                                </div>
                              </div>

                              <!-- Entry Ticket Boarding Pass with Perforated Cutout -->
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9; border:2px dashed #94a3b8; border-radius:16px; padding:24px 20px; text-align:center; margin-bottom:24px;">
                                <tr>
                                  <td align="center">
                                    <span style="font-size:11px; font-weight:800; color:#64748b; text-transform:uppercase; letter-spacing:1px;">Ticket Identifier</span>
                                    <div style="font-size:20px; font-weight:900; font-family:monospace; color:#4338ca; letter-spacing:2px; margin:6px 0 16px;">
                                      %s
                                    </div>

                                    <!-- QR Code Image -->
                                    <div style="display:inline-block; padding:10px; background:#ffffff; border:1px solid #cbd5e1; border-radius:14px; box-shadow:0 8px 16px -4px rgba(0,0,0,0.1); margin-bottom:10px;">
                                      <img src="%s" width="160" height="160" alt="Ticket QR Code" style="display:block; margin:0 auto; width:160px; height:160px; border-radius:8px;" />
                                    </div>
                                    <p style="margin:4px 0 0; font-size:12px; color:#475569; font-weight:600;">
                                      ⚡ Scan QR code directly at the venue entrance gate
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- Payment Details Table -->
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc; border-radius:12px; padding:16px 20px; border:1px solid #e2e8f0; margin-bottom:24px; font-size:13px;">
                                <tr>
                                  <td style="color:#64748b; padding:4px 0;">Total Amount Paid:</td>
                                  <td align="right" style="font-weight:900; color:#0f172a; font-size:15px; padding:4px 0;">%s %s</td>
                                </tr>
                                <tr>
                                  <td style="color:#64748b; padding:4px 0;">Payment Status:</td>
                                  <td align="right" style="font-weight:700; color:#15803d; padding:4px 0;">%s</td>
                                </tr>
                                <tr>
                                  <td style="color:#64748b; padding:4px 0;">Payment Reference:</td>
                                  <td align="right" style="font-family:monospace; color:#475569; font-size:12px; padding:4px 0;">%s</td>
                                </tr>
                              </table>

                              <!-- Venue Entry Guidelines -->
                              <div style="background:#eff6ff; border-left:4px solid #3b82f6; padding:14px 18px; border-radius:8px; font-size:12px; color:#1e40af; line-height:1.6;">
                                <strong style="font-size:13px;">Important Venue Instructions:</strong>
                                <ul style="margin:6px 0 0; padding-left:18px;">
                                  <li>Please arrive at least 15 minutes before showtime.</li>
                                  <li>Have this digital ticket ready on your mobile screen for instant turnstile check-in.</li>
                                  <li>Outside food and beverages are not allowed inside the theater.</li>
                                </ul>
                              </div>

                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background-color:#0f172a; padding:28px 32px; text-align:center; color:#94a3b8; font-size:12px; border-top:1px solid #1e293b;">
                              <p style="margin:0 0 8px; color:#cbd5e1; font-weight:700; font-size:13px;">Thank you for using TicketHub!</p>
                              <p style="margin:0; font-size:11px; color:#64748b;">
                                Need help or modifications? Contact our support team at <a href="mailto:support@tickethub.com" style="color:#818cf8; text-decoration:none;">support@tickethub.com</a>
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                eventTitle,
                customerName,
                event.getBookingReference(),
                ticketStatus,
                eventTitle,
                showDateStr,
                startTimeStr,
                endTimeStr,
                venueName,
                venueAddress,
                screenName,
                seatsHtml,
                ticketNumber,
                qrImageUrl,
                amountStr,
                currency,
                paymentStatus,
                paymentReference
        );
    }

    private String buildPaymentSuccessText(
            String customerName, String paymentStatus, String paymentIdDisplay, String razorpayRef,
            String razorpayOrderId, PaymentSuccessEvent event, String paymentMethod, String amountDisplay,
            String currency, String paidAt) {

        return """
                Hi %s,

                Your payment transaction has been successfully completed! Here are your complete transaction details:

                ==================================================
                PAYMENT TRANSACTION RECEIPT
                ==================================================
                Transaction Status : %s
                Payment ID         : %s
                Razorpay Reference : %s
                Razorpay Order ID  : %s
                Booking ID         : %s
                Payment Method     : %s
                Amount Paid        : %s %s
                Paid At            : %s
                ==================================================

                Your booking is now being confirmed and your ticket is being generated.
                A separate Ticket Confirmation email with your confirmed ticket details and QR code will follow shortly.

                Thank you for choosing TicketHub!

                Regards,
                TicketHub Team
                """.formatted(
                customerName,
                paymentStatus,
                paymentIdDisplay,
                razorpayRef,
                razorpayOrderId,
                event.getBookingId() != null ? event.getBookingId() : "N/A",
                paymentMethod,
                amountDisplay,
                currency,
                paidAt
        );
    }

    private String buildPaymentSuccessHtml(
            String customerName, String paymentStatus, String paymentIdDisplay, String razorpayRef,
            String razorpayOrderId, PaymentSuccessEvent event, String paymentMethod, String amountDisplay,
            String currency, String paidAt) {

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Payment Receipt</title>
                </head>
                <body style="margin:0; padding:0; background-color:#0f172a; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color:#1e293b; line-height:1.5;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0f172a; padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="width:100%%; max-width:600px; background-color:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 25px 50px -12px rgba(0,0,0,0.5); border:1px solid #334155;">
                          
                          <!-- Green Emerald Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg, #064e3b 0%%, #059669 100%%); padding:36px 32px 30px; text-align:center;">
                              <div style="display:inline-block; padding:5px 16px; background:rgba(255,255,255,0.2); border:1px solid rgba(255,255,255,0.3); border-radius:9999px; font-size:12px; font-weight:700; color:#ffffff; text-transform:uppercase; letter-spacing:1px; margin-bottom:12px;">
                                ✓ Payment Confirmed
                              </div>
                              <h1 style="margin:0; color:#ffffff; font-size:26px; font-weight:900;">
                                Transaction Receipt
                              </h1>
                              <p style="margin:6px 0 0; color:#d1fae5; font-size:14px;">
                                Thank you %s! Your payment was processed successfully.
                              </p>
                            </td>
                          </tr>

                          <!-- Amount Big Banner -->
                          <tr>
                            <td style="background-color:#f0fdf4; padding:24px 32px; text-align:center; border-bottom:1px solid #bbf7d0;">
                              <span style="font-size:12px; font-weight:700; color:#166534; text-transform:uppercase; letter-spacing:1px;">Amount Received</span>
                              <div style="font-size:32px; font-weight:900; color:#14532d; margin-top:4px;">
                                %s %s
                              </div>
                              <span style="display:inline-block; margin-top:8px; padding:3px 12px; background:#bbf7d0; color:#14532d; border-radius:9999px; font-size:11px; font-weight:800; text-transform:uppercase;">
                                ● %s
                              </span>
                            </td>
                          </tr>

                          <!-- Transaction Table -->
                          <tr>
                            <td style="padding:28px 32px;">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f8fafc; border-radius:12px; padding:18px 20px; border:1px solid #e2e8f0; font-size:13px;">
                                <tr>
                                  <td style="color:#64748b; padding:8px 0; border-bottom:1px solid #e2e8f0;">Booking ID:</td>
                                  <td align="right" style="font-weight:700; color:#0f172a; padding:8px 0; border-bottom:1px solid #e2e8f0;">#%s</td>
                                </tr>
                                <tr>
                                  <td style="color:#64748b; padding:8px 0; border-bottom:1px solid #e2e8f0;">Payment Method:</td>
                                  <td align="right" style="font-weight:700; color:#0f172a; padding:8px 0; border-bottom:1px solid #e2e8f0;">%s</td>
                                </tr>
                                <tr>
                                  <td style="color:#64748b; padding:8px 0; border-bottom:1px solid #e2e8f0;">Razorpay Reference:</td>
                                  <td align="right" style="font-family:monospace; color:#475569; padding:8px 0; border-bottom:1px solid #e2e8f0;">%s</td>
                                </tr>
                                <tr>
                                  <td style="color:#64748b; padding:8px 0; border-bottom:1px solid #e2e8f0;">Razorpay Order ID:</td>
                                  <td align="right" style="font-family:monospace; color:#475569; padding:8px 0; border-bottom:1px solid #e2e8f0;">%s</td>
                                </tr>
                                <tr>
                                  <td style="color:#64748b; padding:8px 0;">Transaction Time:</td>
                                  <td align="right" style="color:#475569; padding:8px 0;">%s</td>
                                </tr>
                              </table>

                              <div style="margin-top:20px; padding:14px; background:#f0fdf4; border-radius:10px; border:1px solid #bbf7d0; font-size:13px; color:#166534; text-align:center;">
                                🎟️ <strong>Ticket Issued:</strong> Your official Entry Pass with QR verification code has been dispatched to your email.
                              </div>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background-color:#0f172a; padding:24px 32px; text-align:center; color:#94a3b8; font-size:12px;">
                              <p style="margin:0 0 6px; color:#cbd5e1; font-weight:700;">TicketHub Payments</p>
                              <p style="margin:0; font-size:11px; color:#64748b;">
                                Need help? Email us at <a href="mailto:support@tickethub.com" style="color:#818cf8; text-decoration:none;">support@tickethub.com</a>
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                customerName,
                amountDisplay,
                currency,
                paymentStatus,
                event.getBookingId() != null ? event.getBookingId() : "N/A",
                paymentMethod,
                razorpayRef,
                razorpayOrderId,
                paidAt
        );
    }

    private String formatSeats(BookingConfirmedEvent event) {
        if (event.getSeats() == null || event.getSeats().isEmpty()) {
            return "N/A";
        }
        return event.getSeats().stream()
                .map(this::formatSingleSeat)
                .collect(Collectors.joining(", "));
    }

    private String formatSeatsHtml(BookingConfirmedEvent event) {
        if (event.getSeats() == null || event.getSeats().isEmpty()) {
            return "<span style=\"color:#64748b;\">N/A</span>";
        }
        return event.getSeats().stream().map(s -> {
            String label = s.getSeatNumber() != null ? s.getSeatNumber() : "Seat";
            String type = s.getSeatType() != null ? s.getSeatType() : "STANDARD";
            String price = s.getPrice() != null ? " • " + s.getPrice().toPlainString() : "";
            return "<span style=\"display:inline-block; background:#e0e7ff; color:#3730a3; padding:6px 14px; border-radius:9999px; font-size:13px; font-weight:800; margin:4px 4px 4px 0; border:1px solid #c7d2fe;\">" +
                    "💺 " + label + " (" + type + price + ")</span>";
        }).collect(Collectors.joining(" "));
    }

    private String formatSingleSeat(SeatInfo seat) {
        StringBuilder sb = new StringBuilder();
        if (seat.getRowName() != null && !seat.getRowName().isBlank()
                && (seat.getSeatNumber() == null || !seat.getSeatNumber().startsWith(seat.getRowName()))) {
            sb.append("Row ").append(seat.getRowName()).append(" ");
        }
        sb.append(seat.getSeatNumber() != null ? seat.getSeatNumber() : "");
        if (seat.getSeatType() != null && !seat.getSeatType().isBlank()) {
            sb.append(" (").append(seat.getSeatType());
            if (seat.getPrice() != null) {
                sb.append(" - ").append(seat.getPrice().toPlainString());
            }
            sb.append(")");
        } else if (seat.getPrice() != null) {
            sb.append(" (").append(seat.getPrice().toPlainString()).append(")");
        }
        return sb.toString().trim();
    }
}
