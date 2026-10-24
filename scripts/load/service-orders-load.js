import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:18080';
const ADMIN_USERNAME = __ENV.ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'changeme123';

export const options = {
  stages: [
    { duration: '1m', target: 50 }, // rampa até 50 VUs em 1 min
    { duration: '3m', target: 50 }, // patamar de 50 VUs por 3 min
    { duration: '1m', target: 0 },  // descida até 0 VUs em 1 min
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'], // menos de 1% de falhas
    http_req_duration: ['p(95)<2000'], // 95% das requisições abaixo de 2s
  },
};

function generateCpf() {
  const rnd = (n) => Math.floor(Math.random() * n);
  const d = Array.from({ length: 9 }, () => rnd(10));

  let v1 = d.reduce((acc, digit, idx) => acc + digit * (10 - idx), 0);
  v1 = (v1 * 10) % 11;
  if (v1 >= 10) v1 = 0;
  d.push(v1);

  let v2 = d.reduce((acc, digit, idx) => acc + digit * (11 - idx), 0);
  v2 = (v2 * 10) % 11;
  if (v2 >= 10) v2 = 0;
  d.push(v2);

  return `${d.slice(0, 3).join('')}.${d.slice(3, 6).join('')}.${d.slice(6, 9).join('')}-${d.slice(9, 11).join('')}`;
}

function generatePlate() {
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const rL = () => letters[Math.floor(Math.random() * letters.length)];
  const rD = () => Math.floor(Math.random() * 10);
  return `${rL()}${rL()}${rL()}${rD()}${rL()}${rD()}${rD()}`;
}

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username: ADMIN_USERNAME, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, {
    'login succeeded (200)': (r) => r.status === 200,
  });

  const token = loginRes.json('token');
  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };

  const cpf = generateCpf();
  const customerRes = http.post(
    `${BASE_URL}/api/customers`,
    JSON.stringify({
      name: 'Load Test Customer',
      document: cpf,
      contactInfo: {
        email: `loadtest-${Date.now()}@workshop.local`,
        phone: '+5511999998888',
      },
    }),
    { headers: authHeaders }
  );

  check(customerRes, {
    'customer created (201)': (r) => r.status === 201,
  });

  const customerId = customerRes.json('id');
  const plate = generatePlate();
  const vehicleRes = http.post(
    `${BASE_URL}/api/vehicles`,
    JSON.stringify({
      customerId: customerId,
      licensePlate: plate,
      brand: 'Toyota',
      model: 'Corolla',
      year: 2022,
      color: 'Preto',
      mileage: 15000,
    }),
    { headers: authHeaders }
  );

  check(vehicleRes, {
    'vehicle created (201)': (r) => r.status === 201,
  });

  const vehicleId = vehicleRes.json('id');
  const vehicleSnapshot = {
    licensePlate: plate,
    brand: 'Toyota',
    model: 'Corolla',
    year: 2022,
  };

  return {
    token,
    customerId,
    vehicleId,
    vehicleSnapshot,
  };
}

export default function (data) {
  const authHeaders = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${data.token}`,
  };

  // Alterna entre GET /api/service-orders e POST /api/service-orders
  if (__ITER % 2 === 0) {
    const listRes = http.get(`${BASE_URL}/api/service-orders?page=0&size=20`, {
      headers: authHeaders,
    });
    check(listRes, {
      'list service orders succeeded (200)': (r) => r.status === 200,
    });
  } else {
    const createRes = http.post(
      `${BASE_URL}/api/service-orders`,
      JSON.stringify({
        customerId: data.customerId,
        vehicleId: data.vehicleId,
        vehicleSnapshot: data.vehicleSnapshot,
        priority: 'NORMAL',
        initialAssessment: `Automated assessment VU ${__VU} iter ${__ITER}`,
      }),
      { headers: authHeaders }
    );
    check(createRes, {
      'create service order succeeded (201)': (r) => r.status === 201,
    });
  }

  sleep(0.1);
}
