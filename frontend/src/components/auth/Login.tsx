import { useState, useEffect } from 'react';
import { Box, Button, TextField, Typography, Alert } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../providers/AuthProvider.tsx';

export default function Login() {
  const { login, isLoggedIn } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [formData, setFormData] = useState({
    username: '',
    password: '',
  });

  useEffect(() => {
    if (isLoggedIn) navigate('/channels');
  }, [isLoggedIn, navigate]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login(formData.username, formData.password);
      navigate('/channels');
    } catch (err) {
      setError('Invalid username or password');
    }
  };

  return (
    <Box sx={{
      minHeight: '100vh',
      width: '100vw',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      bgcolor: '#313338',
    }}>
      <Box sx={{
        width: '100%',
        maxWidth: 420,
        bgcolor: '#2b2d31',
        borderRadius: 2,
        p: { xs: 3, sm: 4 },
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        mx: 2,
      }}>

        {/* Heading */}
        <Typography
          component="h1"
          sx={{
            fontSize: 24,
            fontWeight: 700,
            color: '#f2f3f5',
            mb: 0.5,
            textAlign: 'center',
          }}
        >
          Welcome back!
        </Typography>
       

        <Box component="form" onSubmit={handleSubmit} sx={{ width: '100%' }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2, bgcolor: '#3c1f1f', color: '#f2f3f5' }}>
              {error}
            </Alert>
          )}

          <Typography sx={fieldLabel}>Username</Typography>
          <TextField
            required fullWidth autoFocus
            id="username" name="username"
            autoComplete="username"
            value={formData.username}
            onChange={handleChange}
            sx={inputSx}
          />

          <Typography sx={fieldLabel}>Password</Typography>
          <TextField
            required fullWidth
            id="password" name="password"
            type="password" autoComplete="current-password"
            value={formData.password}
            onChange={handleChange}
            sx={inputSx}
          />

          <Button
            type="submit" fullWidth variant="contained"
            sx={{
              mt: 3, py: 1.25,
              bgcolor: '#5865f2',
              fontWeight: 600, fontSize: 15,
              textTransform: 'none',
              borderRadius: 1.5,
              '&:hover': { bgcolor: '#4752c4' },
            }}
          >
            Log In
          </Button>

          <Box sx={{ mt: 1.5 }}>
            <Typography sx={{ fontSize: 13, color: '#949ba4', display: 'inline' }}>
              Need an account?{' '}
            </Typography>
            <Typography
              component="span"
              onClick={() => navigate('/register')}
              sx={{
                fontSize: 13,
                color: '#00a8fc',
                cursor: 'pointer',
                '&:hover': { textDecoration: 'underline' },
              }}
            >
              Register
            </Typography>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}

const fieldLabel = {
  fontSize: 11,
  fontWeight: 700,
  color: '#b5bac1',
  letterSpacing: '.05em',
  textTransform: 'uppercase',
  mb: 0.75,
  mt: 2,
};

const inputSx = {
  '& .MuiOutlinedInput-root': {
    bgcolor: '#1e1f22',
    borderRadius: 1.5,
    color: '#f2f3f5',
    fontSize: 15,
    '& fieldset': { borderColor: '#1e1f22' },
    '&:hover fieldset': { borderColor: '#5865f2' },
    '&.Mui-focused fieldset': { borderColor: '#5865f2' },
  },
  '& .MuiInputBase-input': { py: 1.25 },
};