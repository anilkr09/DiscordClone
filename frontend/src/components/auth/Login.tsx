import { useState ,useEffect} from 'react';
import { Box, Button, Container, TextField, Typography, Alert } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import authService from '../../services/auth.service';
import { on } from 'events';
// import { useWebSocketTopic } from '../../services/WebSocketProvider';
import { useAuth } from '../../services/AuthProvider.tsx';
export default function Login() {

    const { login,signup } = useAuth();
    
    // useEffect(() => {
    //     authService.getConnection();
    // }, []);
    // const { messages, sendMessage, clearMessages, isConnected } = useWebSocketTopic("/app/status");

    // useEffect(() => {
    //     if (messages.length > 0) {
    //       console.log("📩 -----------New Message:", messages[messages.length - 1]); // Logs latest message
    //     }
    //   }, [messages]);

    //   useEffect(() => {
    //     if (isConnected) {

    //   sendMessage({
    //     "status": "ONLINE",
    //     "customStatusText": "Working on a project",
    //     "customStatusEmoji": "💻",
    //     "expiresAt": "2025-03-12T18:30:00Z"
      
    //   });

    //       console.log("Connected to WebSocket server");
    //     } else {
    //       console.log("Disconnected from WebSocket server");
    //     }
    //   }, [isConnected]);

    
    const navigate = useNavigate();
    const [error, setError] = useState('');
    const [formData, setFormData] = useState({
        username: '',
        password: ''
    });

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await login(formData.username, formData.password);
            // setUsername(formData.username);
            navigate('/channels');
        } catch (err) {
            setError('Invalid username or password');
        }
    };

    return (
        <Container component="main" maxWidth="xs">
            <Box
                sx={{
                    marginTop: 8,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                }}
            >
                <Typography component="h1" variant="h5">
                    Sign in
                </Typography>
                <Box component="form" onSubmit={handleSubmit} sx={{ mt: 1 }}>
                    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
                    <TextField
                        margin="normal"
                        required
                        fullWidth
                        id="username"
                        label="Username"
                        name="username"
                        autoComplete="username"
                        autoFocus
                        value={formData.username}
                        onChange={handleChange}
                    />
                    <TextField
                        margin="normal"
                        required
                        fullWidth
                        name="password"
                        label="Password"
                        type="password"
                        id="password"
                        autoComplete="current-password"
                        value={formData.password}
                        onChange={handleChange}
                    />
                    <Button
                        type="submit"
                        fullWidth
                        variant="contained"
                        sx={{ mt: 3, mb: 2 }}
                    >
                        Sign In
                    </Button>
                    <Box sx={{ textAlign: 'center' }}>
                        <Typography variant="body2">
                            Don't have an account?{' '}
                            <Button
                                component="a"
                                onClick={() => navigate('/register')}
                                sx={{ textTransform: 'none' }}
                            >
                                Sign up
                            </Button>
                        </Typography>
                    </Box>
                </Box>
            </Box>
        </Container>
    );
}