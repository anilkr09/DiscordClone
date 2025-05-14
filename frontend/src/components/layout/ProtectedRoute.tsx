import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../services/AuthProvider.tsx';

// Loading spinner component
const LoadingSpinner = () => (
  <div className="loading-spinner-container">
    <div className="loading-spinner"></div>
    <p>Checking authentication...</p>
  </div>
);

const ProtectedRoute = () => {
  const { isLoggedIn, authInitialized } = useAuth();
  const location = useLocation();
  
  console.log("🛡️ Protected Route Check:", { 
    path: location.pathname,
    isLoggedIn,
    authInitialized,
    localStorageToken: !!localStorage.getItem("accessToken")
  });

  // Critical: Wait for auth to initialize before making decisions
  if (!authInitialized) {
    console.log("⏳ Auth not yet initialized, showing loading spinner");
    return <LoadingSpinner />;
  }

  // Now we can safely check if user is logged in
  if (!isLoggedIn) {
    console.log("🔒 Not authenticated, redirecting to login from:", location.pathname);
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  
  console.log("✅ User is authenticated, rendering protected content");
  return <Outlet />;
};

export default ProtectedRoute;