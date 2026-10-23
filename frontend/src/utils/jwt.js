export function decodeToken() {
  const token = sessionStorage.getItem('token');
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return {
      id: payload.sub,
      username: sessionStorage.getItem('name') || payload.careId,
      careId: payload.careId,
      role: payload.role,
      facilityName: sessionStorage.getItem('facilityName') || undefined,
      facilityId: payload.facilityId,
    };
  } catch {
    return null;
  }
}
