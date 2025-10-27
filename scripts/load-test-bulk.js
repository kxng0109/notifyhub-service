import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// --- Configuration ---
const TOTAL_RECIPIENTS = 100000;
const CHUNK_SIZE = 500; // How many recipients per API call
const API_URL = 'http://app:8080/api/notifications';

// Create a custom Trend metric to measure the time it takes to process one chunk.
const chunkProcessingTime = new Trend('chunk_processing_time');

// --- Test Options ---
export const options = {
  // This test will run the default function just one time (1 iteration)
  // for one virtual user. We are testing one "bulk job".
  scenarios: {
    single_bulk_job: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10m', // Max 10 min for the whole job
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],   // Fail if more than 1% of chunks fail
    'chunk_processing_time': ['p(95)<2000'], // 95% of chunks should be processed in < 2s
  },
};

// --- Helper function to generate a list of dummy emails ---
function generateRecipients(count) {
  const recipients = [];
  for (let i = 0; i < count; i++) {
    recipients.push(`user_${i}@example.com`);
  }
  return recipients;
}

// --- The Main Test Logic ---
export default function () {
  console.log(`Starting bulk job: ${TOTAL_RECIPIENTS} recipients in chunks of ${CHUNK_SIZE}...`);
  const allRecipients = generateRecipients(TOTAL_RECIPIENTS);
  let chunksSent = 0;

  // Loop through the total recipient list, "chunking" it by CHUNK_SIZE
  for (let i = 0; i < TOTAL_RECIPIENTS; i += CHUNK_SIZE) {
    const chunk = allRecipients.slice(i, i + CHUNK_SIZE);

    const payload = JSON.stringify({
      to: chunk, // Send the list of 500 recipients
      subject: 'Bulk Send Test Newsletter',
      htmlBody: '<p>This is a bulk send test!</p>',
      attachments: []
    });

    const params = {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'BulkSendChunk' }, // Add a tag for better reporting
    };

    // Send the chunk
    const res = http.post(API_URL, payload, params);

    // Check the result
    check(res, {
      'is status 202 (Accepted)': (r) => r.status === 202,
    });

    // Add the request duration to our custom metric
    chunkProcessingTime.add(res.timings.duration);

    chunksSent++;
  }

  console.log(`Bulk job complete. Sent ${chunksSent} chunks.`);
}