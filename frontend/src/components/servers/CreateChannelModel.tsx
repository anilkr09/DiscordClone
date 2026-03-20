import React, { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogTitle,
  Box,
  Typography,
  Button,
  TextField,
  Avatar,
  IconButton
} from "@mui/material";
import AddPhotoAlternateIcon from "@mui/icons-material/AddPhotoAlternate";
import { ChannelType, ServerType } from "../../types/server";

type Step = "entry" | "customize" | "join";

interface Props {
  open: boolean;
  onClose: () => void;
  onCreate: (data: { name: string ;description:string;type:ChannelType;icon?: File}) => void;
}

export default function CreateChannelModel({
  open,
  onClose,
  onCreate
}: Props) {
  const [name, setName] = useState("");
  const [type,setType] = useState<ChannelType>(ChannelType.TEXT);
  const [description,setDescription] = useState("");
  const [icon, setIcon] = useState<File | undefined>();

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
            <TextField
              label="Type"
              placeholder="My Awesome Channel"
              value={type}
              onChange={(e) => setType(ChannelType.TEXT)}
              fullWidth
              autoFocus
            />


            <Box display="flex" justifyContent="space-between">

              <Button
                variant="contained"
                disabled={!name.trim()}
                onClick={() => {
                  onCreate({ name,description,type});
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
