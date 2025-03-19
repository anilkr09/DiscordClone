// components/auth/ProtectedRoute.jsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../services/AuthProvider.tsx';

const ProtectedRoute = () => {
  const { isLoggedIn } = useAuth();
  const location = useLocation();

//   if (true) {
//     // Show loading spinner while checking authentication status
//     return <div className="loading-spinner">Loading...</div>;
//   }
console.log("isLoggedIn"+isLoggedIn);
  if (!isLoggedIn) {
    // Redirect to login page if not authenticated
    // Save the current location to redirect back after login
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // If authenticated, render the child routes
  return <Outlet />;
};

export default ProtectedRoute;