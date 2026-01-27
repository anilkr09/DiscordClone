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
import userService from '../../services/user.service';
import { User } from '../../types/auth';
import { debounce } from 'lodash';
import { useFriends } from '../../hooks/useFriends';

export default function AddFriend() {
  const [username, setUsername] = useState('');
  const [showSnackbar, setShowSnackbar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<User[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  const { addFriend } = useFriends();

  // 🔄 Show snackbar when mutation finishes
  useEffect(() => {
    if (addFriend.data) {
      setShowSnackbar(true);

      if (addFriend.data.success) {
        setUsername('');
        setSuggestions([]);
      }
    }
  }, [addFriend.data]);

  const validateUsername = (value: string) =>
    value.length >= 3 && value.length <= 32;

  // 🔍 Debounced user search
  const searchUsers = debounce(async (query: string) => {
    if (query.length < 3) {
      setSuggestions([]);
      return;
    }

    setIsSearching(true);
    try {
      const users = await userService.searchUsers(query);
      setSuggestions(users);
    } catch (err) {
      console.error('Error searching users:', err);
    } finally {
      setIsSearching(false);
    }
  }, 300);

  const handleUsernameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setUsername(value);
    setError(null);
    addFriend.reset(); // ✅ clear old messages
    searchUsers(value);
  };

  const handleSuggestionClick = (selectedUsername: string) => {
    setUsername(selectedUsername);
    setSuggestions([]);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const trimmed = username.trim();

    if (!trimmed) {
      setError('Username cannot be empty');
      return;
    }

    if (!validateUsername(trimmed)) {
      setError('Username must be between 3 and 32 characters');
      return;
    }

    setError(null);
    addFriend.mutate(trimmed);
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
            disabled={addFriend.isPending}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <PersonAddIcon sx={{ color: '#b9bbbe' }} />
                </InputAdornment>
              ),
              endAdornment: (addFriend.isPending || isSearching) && (
                <InputAdornment position="end">
                  <CircularProgress size={20} color="inherit" />
                </InputAdornment>
              )
            }}
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
                zIndex: 1000
              }}
            >
              {suggestions.map(user => (
                <ListItem
                  key={user.id}
                  onClick={() => handleSuggestionClick(user.username)}
                  sx={{ cursor: 'pointer' }}
                >
                  <ListItemAvatar>
                    <Avatar src={user.avatarUrl}>
                      {user.username.charAt(0).toUpperCase()}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText primary={user.username} />
                </ListItem>
              ))}
            </List>
          )}
        </Box>

        <Button
          type="submit"
          variant="contained"
          disabled={!username.trim() || addFriend.isPending}
          sx={{ bgcolor: '#5865f2' }}
        >
          Send Friend Request
        </Button>
      </form>

      <Snackbar
        open={showSnackbar}
        autoHideDuration={5000}
        onClose={handleCloseSnackbar}
      >
        <Alert
          severity={addFriend.data?.success ? 'success' : 'error'}
          onClose={handleCloseSnackbar}
        >
          {addFriend.data?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
