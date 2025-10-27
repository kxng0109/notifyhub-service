import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// --- Configuration ---
const API_URL = 'http://app:8080/api/notifications';

// Create a custom metric to measure processing time
const requestDuration = new Trend('request_processing_time');

// A small, Base64-encoded text file ("Hello World")
const FAKE_ATTACHMENT_DATA = "SGVsbG8sIFdvcmxkIQ=="; // "Hello, World!"

// --- Test Options ---
export const options = {
    scenarios: {
        // This test will run the default function just one time (1 VU, 1 iteration).
        // The function itself contains the logic for sending the 1,000 recipients in chunks.
        rich_content_job: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '5m',
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.01'], // 99% of requests must succeed
        'request_processing_time': ['p(95)<1000'], // 95% of chunk requests < 1s
    },
};

// --- Helper function to generate a list of dummy emails ---
function generateRecipients(count, offset = 0) {
    const recipients = [];
    for (let i = 0; i < count; i++) {
        recipients.push(`user_${i + offset}@example.com`);
    }
    return recipients;
}

// --- The Main Test Logic ---
export default function () {
    console.log("Starting rich content bulk job...");
    const params = { headers: { 'Content-Type': 'application/json' } };

    // --- Chunk 1: 500 Recipients, HTML-Only ---
    console.log("Sending Chunk 1 (HTML-Only)...");
    const htmlOnlyPayload = JSON.stringify({
        to: generateRecipients(500, 0),
        subject: 'Bulk HTML-Only Test',
        textBody: 'This is a fallback text body.',
        htmlBody: '<h1>Hello World!</h1><p>This is an HTML-only email.</p>',
        attachments: [] // Empty list
    });

    const res1 = http.post(API_URL, htmlOnlyPayload, params);
    check(res1, { 'Chunk 1 is status 202': (r) => r.status === 202 });
    requestDuration.add(res1.timings.duration);


    // --- Chunk 2: 500 Recipients, HTML + Attachment ---
    console.log("Sending Chunk 2 (HTML + Attachment)...");
    const attachmentPayload = JSON.stringify({
        to: generateRecipients(500, 500),
        subject: 'Bulk HTML + Attachment Test',
        textBody: 'This is a fallback text body with an attachment.',
        htmlBody: '<h1>Hello World!</h1><p>This email includes an attachment.</p>',
        attachments: [
            {
                filename: "test-file.txt",
                contentType: "text/plain",
                data: FAKE_ATTACHMENT_DATA
            }
        ]
    });

    const res2 = http.post(API_URL, attachmentPayload, params);
    check(res2, { 'Chunk 2 is status 202': (r) => r.status === 202 });
    requestDuration.add(res2.timings.duration);

    console.log("Rich content bulk job complete.");
}
