import { useState, useEffect } from 'react';
import { 
  Box, 
  Typography, 
  TextField, 
  Button, 
  Alert, 
  InputAdornment,
  CircularProgress,
  Snackbar,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Avatar
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import friendService from '../../services/friend.service';
import userService from '../../services/user.service';
import { AddFriendResponse } from '../../types/friend';
import { User } from '../../types/auth';
import { debounce } from 'lodash';

export default function AddFriend() {
  const [username, setUsername] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [response, setResponse] = useState<AddFriendResponse | null>(null);
  const [showSnackbar, setShowSnackbar] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<User[]>([]);
  const [isSearching, setIsSearching] = useState<boolean>(false);

  // Clear response after 5 seconds
  useEffect(() => {
    if (response) {
      setShowSnackbar(true);
      const timer = setTimeout(() => {
        setShowSnackbar(false);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [response]);

  const validateUsername = (username: string): boolean => {
    // Username should be at least 3 characters and not more than 32 characters
    return username.length >= 3 && username.length <= 32;
  };

  // Debounced search function
  const searchUsers = debounce(async (query: string) => {
    if (query.length < 3) {
      setSuggestions([]);
      return;
    }

    setIsSearching(true);
    try {
      const users = await userService.searchUsers(query);
      setSuggestions(users);
    } catch (error) {
      console.error('Error searching users:', error);
    } finally {
      setIsSearching(false);
    }
  }, 300);

  const handleUsernameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setUsername(value);
    setError(null);
    searchUsers(value);
  };

  const handleSuggestionClick = (selectedUsername: string) => {
    setUsername(selectedUsername);
    setSuggestions([]);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!username.trim()) {
      setError('Username cannot be empty');
      return;
    }

    if (!validateUsername(username.trim())) {
      setError('Username must be between 3 and 32 characters');
      return;
    }

    setError(null);
    setIsSubmitting(true);
    try {
      const result = await friendService.addFriend(username.trim());
      setResponse(result);
      
      if (result.success) {
        setUsername('');
        setSuggestions([]);
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

  const handleCloseSnackbar = () => {
    setShowSnackbar(false);
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
        <Box sx={{ position: 'relative' }}>
          <TextField
            fullWidth
            placeholder="Enter a username"
            value={username}
            onChange={handleUsernameChange}
            error={!!error}
            helperText={error}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <PersonAddIcon sx={{ color: '#b9bbbe' }} />
                </InputAdornment>
              ),
              endAdornment: (isSubmitting || isSearching) && (
                <InputAdornment position="end">
                  <CircularProgress size={20} color="inherit" />
                </InputAdornment>
              ),
              sx: {
                bgcolor: '#202225',
                color: 'white',
                borderRadius: '8px',
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: error ? '#ed4245' : 'transparent'
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: error ? '#ed4245' : 'transparent'
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: error ? '#ed4245' : '#5865f2'
                },
                p: 1
              }
            }}
            FormHelperTextProps={{
              sx: {
                color: '#ed4245',
                ml: 1
              }
            }}
            disabled={isSubmitting}
            sx={{ mb: 2 }}
          />

          {suggestions.length > 0 && (
            <List
              sx={{
                position: 'absolute',
                top: '100%',
                left: 0,
                right: 0,
                bgcolor: '#2f3136',
                borderRadius: '8px',
                mt: 1,
                maxHeight: '200px',
                overflowY: 'auto',
                zIndex: 1000,
                boxShadow: '0 8px 16px rgba(0,0,0,0.24)'
              }}
            >
              {suggestions.map((user) => (
                <ListItem
                  key={user.id}
                  onClick={() => handleSuggestionClick(user.username)}
                  sx={{
                    cursor: 'pointer',
                    '&:hover': {
                      bgcolor: '#36393f'
                    }
                  }}
                >
                  <ListItemAvatar>
                    <Avatar
                      src={user.avatarUrl}
                      sx={{
                        bgcolor: '#ed4245',
                        width: 32,
                        height: 32,
                        fontSize: '14px'
                      }}
                    >
                      {user.username.charAt(0).toUpperCase()}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={user.username}
                    primaryTypographyProps={{
                      sx: { color: '#dcddde' }
                    }}
                  />
                </ListItem>
              ))}
            </List>
          )}
        </Box>
        
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
      
      <Snackbar 
        open={showSnackbar} 
        autoHideDuration={5000} 
        onClose={handleCloseSnackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert 
          onClose={handleCloseSnackbar}
          severity={response?.success ? 'success' : 'error'} 
          sx={{ 
            width: '100%',
            bgcolor: response?.success ? 'rgba(59, 165, 93, 0.1)' : 'rgba(237, 66, 69, 0.1)',
            color: response?.success ? '#3ba55d' : '#ed4245',
            '& .MuiAlert-icon': {
              color: response?.success ? '#3ba55d' : '#ed4245'
            }
          }}
        >
          {response?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
} 