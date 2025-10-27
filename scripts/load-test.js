import http from 'k6/http';
import { check, sleep } from 'k6';

// --- Test Configuration ---
export const options = {
    // Define "virtual users" (VUs) and duration.
    // This stages ramp-up is a professional way to run a test.
    stages: [
        { duration: '30s', target: 20 }, // 1. Ramp up to 20 virtual users over 30 seconds
        { duration: '1m', target: 20 }, // 2. Hold at 20 users for 1 minute (sustained load)
        { duration: '10s', target: 0 },  // 3. Ramp down to 0 users
    ],
    // Define "thresholds" or failure criteria.
    // This will make the test fail if too many requests are errors.
    thresholds: {
        'http_req_failed': ['rate<0.01'],   // Fail if more than 1% of requests error out.
        'http_req_duration': ['p(95)<1000'], // Fail if 95% of requests take longer than 1 second.
    },
};

// --- The Test Logic (Executed by each Virtual User) ---
export default function () {
    // The URL of our NotifyHub app service (inside the Docker network)
    const url = 'http://app:8080/api/notifications';

    // The JSON payload for our request.
    // We'll send a simple plain-text email for this test.
    const payload = JSON.stringify({
        to: ['test.user@example.com'],
        subject: 'K6 Load Test',
        body: 'This is a high-volume test message.',
        htmlBody: null,
        attachments: []
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // Send the POST request
    const res = http.post(url, payload, params);

    // Check if the request was successful
    check(res, {
        'is status 202 (Accepted)': (r) => r.status === 202,
    });

    // Wait for 1 second before sending the next request (per VU)
    //You can comment this out for maximum load
    sleep(1)
}