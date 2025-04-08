import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/auth/Login';
import Register from './components/auth/Register';
import MainLayout from './components/layout/MainLayout';
import ServerView from './components/servers/ServerView';
import FriendsList from './components/friends/FriendsList';
import ProtectedRoute from './components/layout/ProtectedRoute.tsx';
import './App.css';
import { WebSocketProvider } from './services/WebSocketProvider.tsx';
import { AuthProvider } from './services/AuthProvider.tsx';
import { StatusProvider } from './services/StatusProvider.tsx';
import ChatArea from './components/chat/ChatArea.tsx';
import DirectMessage from './components/chat/DirectMessage.tsx';
function App() {
  return (
    <AuthProvider>

    <WebSocketProvider>
      <StatusProvider>
      <Router>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          
          {/* Protected routes under MainLayout */}
          <Route element={<ProtectedRoute />}>
          <Route path="*" element={<Navigate to="/login" />} />
          <Route path="/channels" element={<MainLayout />}>
          
          <Route index element={<Navigate to="/channels/@me" replace />} />
          <Route path="@me" element={<FriendsList />} />
          <Route path="@me/:friendId" element={<DirectMessage />} />
{/* <Route path="/channels/:channelId" element={<Channel />} /> */}


            <Route path="servers" element={<ServerView />} />
            <Route path="servers/:serverId" element={<ServerView />} />
          </Route>
          </Route>

        </Routes>
      </Router>
      </StatusProvider>
     </WebSocketProvider>
    </AuthProvider>
  );
}

export default App;
