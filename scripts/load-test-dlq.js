import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// --- Configuration ---
const API_URL = 'http://app:8080/api/notifications';
const requestDuration = new Trend('request_processing_time');

// --- Test Options ---
export const options = {
    scenarios: {
        // We will send 10 messages, one per second.
        // This is not a load test, but a failure test.
        dlq_failure_injection: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 10, // Send 10 messages
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.01'], // 99% of requests must succeed
    },
};

// --- The Main Test Logic ---
export default function () {
    console.log("Sending a message payload designed to FAIL and trigger the DLQ...");
    const params = { headers: { 'Content-Type': 'application/json' } };

    // --- The Invalid Payload ---
    // This payload is intentionally malformed. The 'data' field is not Base64.
    const invalidPayload = JSON.stringify({
        to: ['dlq-test@example.com'],
        subject: 'DLQ Test (This should fail)',
        htmlBody: '<p>This message will fail processing.</p>',
        attachments: [
            {
                filename: "invalid-file.txt",
                contentType: "text/plain",
                data: "This is NOT valid Base64!!! %%" // This will cause Base64.getDecoder().decode() to fail
            }
        ]
    });

    // --- Send the Request ---
    // We fully expect to get a 202 Accepted, because the API
    // does not know the message is invalid.
    const res = http.post(API_URL, invalidPayload, params);

    // k6 will report this as a SUCCESS, which is correct.
    check(res, {
        'API call was accepted (202-Accepted)': (r) => r.status === 202,
    });

    requestDuration.add(res.timings.duration);
    sleep(1); // Wait 1 second between sends
}
