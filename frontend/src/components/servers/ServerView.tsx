import { useEffect, useState } from 'react';
import { Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { Tag as TagIcon, VolumeUp as VolumeUpIcon, Add as AddIcon } from '@mui/icons-material';
import { useParams } from 'react-router-dom';
import { Server, Channel, ChannelType } from '../../types/server';
import serverService from '../../services/server.service';
import channelService from '../../services/channel.service';
import ChatArea from '../chat/ChatArea';
import CreateChannelModel from '../servers/CreateChannelModel';
import { useChannels } from '../../hooks/useChannels';

  

export default function ServerView() {
 const { serverId } = useParams();  
   const [server, setServer] = useState<Server | null>(null);
    const [selectedChannel, setSelectedChannel] = useState<Channel | null>(null);
const [open, setOpen] = useState(false);
      const {channels, createChannel} = useChannels( serverId ? parseInt(serverId):0); 
const serverIdNum = serverId ? parseInt(serverId) : 0;
      const handleAddChannel = () => {
   setOpen(true); 
  };
    useEffect(() => {
        if (serverId) {
            console.log("Server ID:", serverId);
            loadServer(parseInt(serverId));
            const myChannel: Channel = {
                name: "Default Channel",
                id:1,
                serverId: 1,
                messages: [
                    
                ],
                type:ChannelType.TEXT,
                createdAt: "2025-04-10T15:00:00Z",
                updatedAt: "2025-04-11T09:00:00Z"
            };

            // setSelectedChannel(myChannel);

        }
    }, [serverId,channels]);
    
    const loadServer = async (id: number) => {
        try {
            
            // const data = await serverService.getServer(id);
            
            if (channels.length > 0) {
                setSelectedChannel(channels[0]);
            }
        } catch (error) {
            console.error('Failed to load server:', error);
        }
    };

    return (
        <>
{serverId ? (
  <Box
    sx={{
      width: '250px',
      bgcolor: '#2f3136',
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
    }}
  >
    {/* Header */}
    <Box
      sx={{
        p: 2,
        fontWeight: 'bold',
        borderBottom: '1px solid #26282c',
        cursor: 'pointer',
        color: 'white',
      }}
    >
      {server?.name ?? 'Channels'}
    </Box>

    {/* Channel List */}
    <Box sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
      {channels?.map((channel) => {
        const isSelected = selectedChannel?.id === channel.id;

        return (
          <Box
            key={channel.id}
            onClick={() => setSelectedChannel(channel)}
            sx={{
              display: 'flex',
              alignItems: 'center',
              px: 2,
              py: 1,
              mx: 1,
              borderRadius: '4px',
              cursor: 'pointer',
              color: isSelected ? 'white' : '#b9bbbe',
              bgcolor: isSelected ? '#393c43' : 'transparent',
              '&:hover': {
                bgcolor: '#34373c',
                color: 'white',
              },
            }}
          >
            {/* Icon */}
            <Box sx={{ mr: 1.5, color: '#8e9297' }}>
              {channel.type === ChannelType.TEXT ? (
                <TagIcon fontSize="small" />
              ) : (
                <VolumeUpIcon fontSize="small" />
              )}
            </Box>

            {/* Channel Name */}
            <Box
              sx={{
                fontSize: '14px',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {channel.name}
            </Box>
          </Box>
        );
      })}

      {/* Add Channel */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2,
          py: 1,
          mx: 1,
          mt: 1,
          borderRadius: '4px',
          cursor: 'pointer',
          color: '#b9bbbe',
          '&:hover': {
            bgcolor: '#34373c',
            color: 'white',
          },
        }}
      >
        <Box sx={{ mr: 1.5 }}>
          <AddIcon fontSize="small" />
        </Box>
        <Box           onClick={handleAddChannel}
 sx={{ fontSize: '14px' }}>Add Channel</Box>
      </Box>
    </Box>

    {/* Bottom user profile (optional later) */}
  </Box>
) : null}
<CreateChannelModel
  open={open}
  onClose={() => setOpen(false)}
  onCreate={(data) => {
    // serverService.createServer(data).then((response) => {
    //   console.log('Server created:', response);
    //   navigate(`/channels/${response.id}`);
    // })
    // .catch((error) => {
    //   console.error('Failed to create server:', error);
    // }); 
    createChannel({serverId: serverIdNum, channel: data });
    console.log(data);
  }}
  
/>

            <Box component="main" sx={{ flexGrow: 1 }}>
                {channels.length>0 && selectedChannel!=null&&selectedChannel.id!=null && (
                    <ChatArea  
                        // id={selectedChannel?.id?.toString()||"1"}
                        id={selectedChannel.id.toString()}
                        name={selectedChannel.name}
                    isDM={false}
                />
                )}
            </Box>
        </>
    );
}