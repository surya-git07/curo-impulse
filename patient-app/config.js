// config.js
// Single place for the API URL and the mock switch.
// When the real backend is ready, set EXPO_PUBLIC_API_URL in a .env file
// (EXPO_PUBLIC_API_URL=https://your-ec2-ip) and flip MOCK_MODE to false.

export const API_URL = process.env.EXPO_PUBLIC_API_URL || '';

// While MOCK_MODE is true the app uses local fake data and never calls the network.
// Keep it true until the API contract lands. Venue Wi-Fi can be flaky, so a
// mock mode is also a safe fallback for the live demo.
export const MOCK_MODE = true;