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
import { ServerType } from "../../types/server";

type Step = "entry" | "customize" | "join";

interface Props {
  open: boolean;
  onClose: () => void;
  onCreate: (data: { name: string ;description:string;type:ServerType;icon?: File}) => void;
  onJoin: (inviteLink: string) => void;
}

export default function CreateServerModal({
  open,
  onClose,
  onCreate,
  onJoin
}: Props) {
  const [step, setStep] = useState<Step>("entry");
  const [name, setName] = useState("");
  const [type,setType] = useState<ServerType>(ServerType.PUBLIC);
  const [description,setDescription] = useState("");
  const [icon, setIcon] = useState<File | undefined>();
  const [invite, setInvite] = useState("");

  // 🔑 Reset state whenever modal opens
  useEffect(() => {
    if (open) {
      setStep("entry");
      setName("");
      setDescription("");
      setIcon(undefined);
      setInvite("");
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ textAlign: "center", fontWeight: 700 }}>
        {step === "entry" && "Create a Server"}
        {step === "customize" && "Customize your server"}
        {step === "join" && "Join a Server"}
      </DialogTitle>

      <DialogContent>
        {/* ENTRY STEP */}
        {step === "entry" && (
          <Box display="flex" flexDirection="column" gap={2}>
            <Typography
              variant="body2"
              color="text.secondary"
              textAlign="center"
            >
              Your server is where you and your friends hang out.
            </Typography>

            <Button
              variant="outlined"
              size="large"
              onClick={() => setStep("customize")}
            >
              ➕ Create My Own
            </Button>

            <Button
              variant="outlined"
              size="large"
              onClick={() => setStep("join")}
            >
              🔗 Join a Server
            </Button>
          </Box>
        )}

        {/* JOIN SERVER */}
        {step === "join" && (
          <Box display="flex" flexDirection="column" gap={2}>
            <TextField
              label="Invite Code"
              placeholder="invite code"
              value={invite}
              onChange={(e) => setInvite(e.target.value)}
              fullWidth
              autoFocus
            />

            <Button
              variant="contained"
              disabled={!invite.trim()}
              onClick={() => {
                onJoin(invite);
                onClose();
              }}
            >
              Join Server
            </Button>

            <Button onClick={() => setStep("entry")}>Back</Button>
          </Box>
        )}

        {/* CUSTOMIZE SERVER */}
        {step === "customize" && (
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
              label="Server Name"
              placeholder="My Server"
              value={name}
              onChange={(e) => setName(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Description"
              placeholder="My Awesome Server"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              fullWidth
              autoFocus
            />
            <TextField
              label="Type"
              placeholder="My Awesome Server"
              value={type}
              onChange={(e) => setType(ServerType.PUBLIC)}
              fullWidth
              autoFocus
            />


            <Box display="flex" justifyContent="space-between">
              <Button onClick={() => setStep("entry")}>Back</Button>

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
