// k6 load test used to watch the HPA scale the API (technical-spec D11).
// Run through the container image (see "make load-test"); BASE_URL must be reachable from inside that container,
// hence host.docker.internal instead of localhost.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const CREDENTIALS = JSON.stringify({
    username: __ENV.ADMIN_USERNAME || 'admin',
    password: __ENV.ADMIN_PASSWORD || 'changeme123',
});
const JSON_HEADERS = { 'Content-Type': 'application/json' };
// BCrypt makes every login cost a predictable amount of CPU, which is what drives the CPU-based scaling.
const LOGIN_RATIO = 0.2;

export const options = {
    scenarios: {
        hpa: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 10 },
                { duration: '3m', target: 20 },
                { duration: '1m', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<2000'],
    },
};

function login() {
    const res = http.post(`${BASE_URL}/api/auth/login`, CREDENTIALS, {
        headers: JSON_HEADERS,
        tags: { name: 'login' },
    });
    check(res, { 'login returns 200': (r) => r.status === 200 });
    return res;
}

// Runs once; the token (valid for 1 h) is shared by every virtual user.
export function setup() {
    const res = login();
    if (res.status !== 200) {
        throw new Error(`Login failed with status ${res.status}; is the API reachable at ${BASE_URL}?`);
    }
    return { token: res.json('token') };
}

export default function (data) {
    if (Math.random() < LOGIN_RATIO) {
        login();
    } else {
        const res = http.get(`${BASE_URL}/api/service-orders`, {
            headers: { Authorization: `Bearer ${data.token}` },
            tags: { name: 'list-service-orders' },
        });
        check(res, { 'list returns 200': (r) => r.status === 200 });
    }
    sleep(0.5);
}
