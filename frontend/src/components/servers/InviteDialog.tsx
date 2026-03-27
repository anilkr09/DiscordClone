import {
  Dialog,
  DialogTitle,
  DialogContent,
  Box,
  Typography,
  TextField,
  Button,
  IconButton,
  InputAdornment,
  Avatar,
  CircularProgress
} from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import RefreshIcon from "@mui/icons-material/Refresh";
import { useEffect, useState } from "react";
import api from "../../services/api";

type Props = {
  open: boolean;
  onClose: () => void;
  serverId: string;
};

export default function InviteDialog({ open, onClose, serverId }: Props) {
  const [inviteUrl, setInviteUrl] = useState("");
    const [inviteCode, setInviteCode] = useState("");

  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
const [codeCopied, setCodeCopied] = useState(false);
  // 🔹 Fetch or create invite
  const fetchInvite = async () => {
    try {
      setLoading(true);

      // 👉 Replace with your API
     
    const response = await api.post<any>(`/invites/create?serverId=${serverId}`);
      

      const data =  response.data;

      setInviteUrl(`${data.inviteUrl}`);
      setInviteCode(data.code);
    } catch (err) {
      console.error("Failed to fetch invite", err);
    } finally {
      setLoading(false);
    }
  };

  // 🔹 Fetch when dialog opens
  useEffect(() => {
    if (open) {
      fetchInvite();
      setCopied(false);
    }
  }, [open]);

  // 🔹 Copy handler
  const handleCopy = async () => {
    if (!inviteUrl) return;

    await navigator.clipboard.writeText(inviteUrl);
    setCopied(true);

    setTimeout(() => setCopied(false), 2000);
  };
  const handleCopyCode = async () => {
    if (!inviteCode) return;

    await navigator.clipboard.writeText(inviteCode);
    setCodeCopied(true);

    setTimeout(() => setCodeCopied(false), 2000);
  }

  // 🔹 Regenerate link
  const handleRegenerate = async () => {
    try {
      setLoading(true);

      // 👉 Replace with your API
      const response = await api.post<any>(`/invites/create?serverId=${serverId}`);
      

      const data =  response.data;

      setInviteUrl(`${data.inviteUrl}`);
        setInviteCode(data.code);

      setCopied(false);
    } catch (err) {
      console.error("Failed to regenerate invite", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ textAlign: "center", fontWeight: 700 }}>
        Invite People
      </DialogTitle>

      <DialogContent>
        <Box display="flex" flexDirection="column" gap={3}>
          
          {/* Server Icon (optional) */}
          <Box display="flex" justifyContent="center">
            <Avatar sx={{ width: 64, height: 64, bgcolor: "#5865F2" }}>
              S
            </Avatar>
          </Box>

          {/* Info */}
          <Box textAlign="center">
            <Typography variant="body2" color="text.secondary">
              Share this link to invite others to your server
            </Typography>
          </Box>

          {/* Invite Link */}
          <TextField
            value={inviteUrl}
            fullWidth
            InputProps={{
              readOnly: true,
              endAdornment: (
                <InputAdornment position="end">
                  <IconButton onClick={handleCopy}>
                    <ContentCopyIcon />
                  </IconButton>
                </InputAdornment>
              )
            }}
          />

          {/* Copy Button */}
          <Button
            variant="contained"
            onClick={handleCopy}
            disabled={!inviteUrl || loading}
          >
            {copied ? "Copied!" : "Copy Invite Link"}
          </Button>
          <Button
            variant="contained"
            onClick={handleCopyCode}
            disabled={!inviteCode || loading}
          >
            {codeCopied ? "Copied!" : "Copy Invite Code"}
          </Button>

          {/* Regenerate */}
          <Button
            variant="text"
            color="secondary"
            startIcon={<RefreshIcon />}
            onClick={handleRegenerate}
            disabled={loading}
          >
            Generate New Link
          </Button>

          {/* Expiry Info */}
          <Typography
            variant="caption"
            color="text.secondary"
            textAlign="center"
          >
            Link expires in 24 hours
          </Typography>

          {/* Loading Indicator */}
          {loading && (
            <Box display="flex" justifyContent="center">
              <CircularProgress size={24} />
            </Box>
          )}
        </Box>
      </DialogContent>
    </Dialog>
  );
}