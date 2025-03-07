import { useState } from 'react';
import { 
  Box, 
  Typography, 
  TextField, 
  Button, 
  Alert, 
  InputAdornment,
  CircularProgress
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import friendService from '../../services/friend.service';
import { AddFriendResponse } from '../../types/friend';

export default function AddFriend() {
  const [username, setUsername] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [response, setResponse] = useState<AddFriendResponse | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!username.trim()) {
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await friendService.addFriend(username.trim());
      setResponse(result);
      
      if (result.success) {
        setUsername('');
      }
    } catch (error) {
      console.error('Error adding friend:', error);
      setResponse({
        success: false,
        message: 'An unexpected error occurred. Please try again.'
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Box sx={{ p: 3, maxWidth: '100%' }}>
      <Typography variant="h6" sx={{ mb: 1, fontWeight: 'bold' }}>
        ADD FRIEND
      </Typography>
      
      <Typography sx={{ color: '#96989d', mb: 3, fontSize: '14px' }}>
        You can add friends with their Discord username. It's cAsE sEnSiTiVe!
      </Typography>
      
      <form onSubmit={handleSubmit}>
        <TextField
          fullWidth
          placeholder="Enter a username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <PersonAddIcon sx={{ color: '#b9bbbe' }} />
              </InputAdornment>
            ),
            endAdornment: isSubmitting && (
              <InputAdornment position="end">
                <CircularProgress size={20} color="inherit" />
              </InputAdornment>
            ),
            sx: {
              bgcolor: '#202225',
              color: 'white',
              borderRadius: '8px',
              '& .MuiOutlinedInput-notchedOutline': {
                borderColor: 'transparent'
              },
              '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: 'transparent'
              },
              '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: '#5865f2'
              },
              p: 1
            }
          }}
          disabled={isSubmitting}
          sx={{ mb: 2 }}
        />
        
        <Button
          type="submit"
          variant="contained"
          disabled={!username.trim() || isSubmitting}
          sx={{
            bgcolor: '#5865f2',
            '&:hover': { bgcolor: '#4752c4' },
            '&.Mui-disabled': { bgcolor: '#3c4380', color: '#8e9297' }
          }}
        >
          Send Friend Request
        </Button>
      </form>
      
      {response && (
        <Alert 
          severity={response.success ? 'success' : 'error'} 
          sx={{ mt: 2, bgcolor: response.success ? 'rgba(59, 165, 93, 0.1)' : 'rgba(237, 66, 69, 0.1)' }}
        >
          {response.message}
        </Alert>
      )}
    </Box>
  );
} 