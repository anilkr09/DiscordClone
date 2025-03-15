import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/auth/Login';
import Register from './components/auth/Register';
import MainLayout from './components/layout/MainLayout';
import ServerView from './components/servers/ServerView';
import FriendsList from './components/friends/FriendsList';

import './App.css';
import { WebSocketProvider } from './services/WebSocketProvider';
import { AuthProvider } from './services/AuthProvider.tsx';
function App() {
  return (
    <AuthProvider>

    <WebSocketProvider>
      <Router>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          
          {/* Protected routes under MainLayout */}
          <Route path="/app" element={<MainLayout />}>
            <Route index element={<Navigate to="friends" replace />} />
            <Route path="friends" element={<FriendsList />} />
            <Route path="servers" element={<ServerView />} />
            <Route path="servers/:serverId" element={<ServerView />} />
          </Route>
        </Routes>
      </Router>
     </WebSocketProvider>
    </AuthProvider>
  );
}

export default App;
