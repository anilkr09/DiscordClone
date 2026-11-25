import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  Box,
  Button,
  TextField,
  Avatar,
  IconButton
} from "@mui/material";
import AddPhotoAlternateIcon from "@mui/icons-material/AddPhotoAlternate";
import { ChannelPayload } from "../../types/server";


interface Props {
  open: boolean;
  onClose: () => void;
  onCreate: (data: ChannelPayload) => void;
}

export default function CreateChannelModel({
  open,
  onClose,
  onCreate
}: Props) {
  const [name, setName] = useState("");
  const [description,setDescription] = useState("");
  const [, setIcon] = useState<File | undefined>();

  // 🔑 Reset state whenever modal opens
  useEffect(() => {
    if (open) {
      setName("");
      setDescription("");
      setIcon(undefined);
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ textAlign: "center", fontWeight: 700 }}>
      
        {"Channel Details"}
       
      </DialogTitle>

      <DialogContent>
      
    
        {/* CUSTOMIZE SERVER */}
        { (
          <Box display="flex" flexDirection="column" gap={3}>
            <Box display="flex" justifyContent="center">
              <IconButton component="label">
                <Avatar
                  sx={{
                    width: 80,
                    height: 80,
                    bgcolor: "#5865F2"
                  }}
                >
                  <AddPhotoAlternateIcon />
                </Avatar>

                <input
                  hidden
                  type="file"
                  accept="image/*"
                  onChange={(e) => setIcon(e.target.files?.[0])}
                />
              </IconButton>
            </Box>
   <TextField
              label="Channel Name"
              placeholder="My Channel"
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Description"
              placeholder="My Awesome Channel"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              fullWidth
              autoFocus
            />

            <Box display="flex" justifyContent="space-between">

              <Button
                variant="contained"
                disabled={!name.trim()}
                onClick={() => {
                  onCreate({ name, description });
                  onClose();
                }}
              >
                Create
              </Button>
            </Box>
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );
}
