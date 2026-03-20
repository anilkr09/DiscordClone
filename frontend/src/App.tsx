import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/auth/Login';
import Register from './components/auth/Register';
import MainLayout from './components/layout/MainLayout';
import ServerView from './components/servers/ServerView';
import FriendsList from './components/friends/FriendsList';
import ProtectedRoute from './components/layout/ProtectedRoute.tsx';
import './App.css';
import { WebSocketProvider } from './providers/WebSocketProvider.tsx';
import { AuthProvider } from './providers/AuthProvider.tsx';
import { StatusProvider } from './providers/StatusProvider.tsx';
import DirectMessage from './components/chat/DirectMessage.tsx';
import { Provider } from "react-redux";
import { store } from "./store/store";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./lib/reactQuery";
import { Toaster } from "react-hot-toast";
function App() {
  return (<>
          <Toaster position="bottom-right" />

    <AuthProvider>
      <Provider store={store}>  
    <WebSocketProvider>
      <StatusProvider>
        <QueryClientProvider client={queryClient}>
      <Router>
        <Routes>
          <Route path="/" element={<Navigate to="/channels" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          
          {/* Protected routes under MainLayout */}
          <Route element={<ProtectedRoute />}>
          <Route path="*" element={<Navigate to="/login" />} />
          <Route path="/channels" element={<MainLayout />}>
          
          <Route index element={<Navigate to="/channels/@me" replace />} />
          <Route path="@me" element={<FriendsList />} />
          <Route path="@me/:friendId" element={<DirectMessage />} />
          <Route path=":serverId" element={<ServerView />} />
            <Route path=":serverId/:channelId" element={<ServerView />} />

          </Route>
          </Route>

        </Routes>
      </Router>
      </QueryClientProvider>
      </StatusProvider>
     </WebSocketProvider>
      </Provider>
    </AuthProvider>
   </>
  );
}

export default App;
