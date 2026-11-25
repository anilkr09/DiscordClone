import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/auth/Login';
import Register from './components/auth/Register';
import MainLayout from './components/layout/MainLayout';
import ServerView from './components/servers/ServerView';
import FriendsList from './components/friends/FriendsList';
import ProtectedRoute from './components/layout/ProtectedRoute.tsx';
import HomeView from './components/layout/HomeView';
import './App.css';
import { AuthProvider } from './providers/AuthProvider.tsx';
import DirectMessage from './components/chat/DirectMessage.tsx';
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./lib/reactQuery";
import { Toaster } from "react-hot-toast";
import { RealtimeSession } from "./realtime/RealtimeSession";
import { RouteBoundary } from "./components/common/RouteBoundary";

function App() {
  return (
    <>
      <Toaster position="bottom-right" />

      <AuthProvider>
        <QueryClientProvider client={queryClient}>
          <RealtimeSession>
                <Router>
                  <Routes>
                    {/* Public routes */}
                    <Route path="/" element={<Navigate to="/channels" replace />} />
                    <Route path="/login" element={<RouteBoundary><Login /></RouteBoundary>} />
                    <Route path="/register" element={<RouteBoundary><Register /></RouteBoundary>} />

                    {/* Protected routes */}
                    <Route element={<ProtectedRoute />}>
                      <Route path="*" element={<Navigate to="/login" />} />

                      {/* MainLayout is the 4-part shell — renders ServerRail + Outlet */}
                      <Route path="/channels" element={<RouteBoundary><MainLayout /></RouteBoundary>}>

                        {/* /channels → redirect to @me */}
                        <Route index element={<Navigate to="/channels/@me" replace />} />

                        {/* @me routes — HomeView renders DM sidebar + its own Outlet */}
                        <Route path="@me" element={<RouteBoundary><HomeView /></RouteBoundary>}>
                          {/* /channels/@me → show FriendsList in Part 3 */}
                          <Route index element={<RouteBoundary><FriendsList /></RouteBoundary>} />
                          {/* /channels/@me/:friendId → show DirectMessage in Part 3 */}
                          <Route path=":friendId" element={<RouteBoundary><DirectMessage /></RouteBoundary>} />
                        </Route>

                        {/* Server routes — ServerView renders its own channel sidebar + chat */}
                        <Route path=":serverId" element={<RouteBoundary><ServerView /></RouteBoundary>} />
                        <Route path=":serverId/:channelId" element={<RouteBoundary><ServerView /></RouteBoundary>} />

                      </Route>
                    </Route>
                  </Routes>
                </Router>
          </RealtimeSession>
        </QueryClientProvider>
      </AuthProvider>
    </>
  );
}

export default App;