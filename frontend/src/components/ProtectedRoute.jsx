import { Navigate } from 'react-router-dom';
import { decodeToken } from '../utils/jwt';

function ProtectedRoute({ allowedRoles, children }) {
  const user = decodeToken();

  if (!user) {
    return <Navigate to="/" replace />;
  }

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export default ProtectedRoute;