import { useEffect, useState } from 'react';
import { Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { Tag as TagIcon, VolumeUp as VolumeUpIcon, Add as AddIcon } from '@mui/icons-material';
import { useParams } from 'react-router-dom';
import { Server, Channel, ChannelType } from '../../types/server';
import serverService from '../../services/server.service';
import ChatArea from '../chat/ChatArea';


const DRAWER_WIDTH = 240;

export default function ServerView() {
    const { serverId } = useParams();
    const [server, setServer] = useState<Server | null>(null);
    const [selectedChannel, setSelectedChannel] = useState<Channel | null>(null);

    useEffect(() => {
        if (serverId) {
            loadServer(parseInt(serverId));
            const myChannel: Channel = {
                name: "myChannel",
                id:1,
                serverId: 1,
                messages: [
                    
                ],
                type:ChannelType.TEXT,
                createdAt: "2025-04-10T15:00:00Z",
                updatedAt: "2025-04-11T09:00:00Z"
            };

            setSelectedChannel(myChannel);

        }
    }, [serverId]);

    const loadServer = async (id: number) => {
        try {
            const data = await serverService.getServer(id);
            setServer(data);
            if (data.channels && data.channels.length > 0) {
                setSelectedChannel(data.channels[0]);
            }
        } catch (error) {
            console.error('Failed to load server:', error);
        }
    };

    return (
        <>
            {/* <Drawer
                variant="permanent"
                sx={{
                    width: DRAWER_WIDTH,
                    flexShrink: 0,
                    '& .MuiDrawer-paper': {
                        width: DRAWER_WIDTH,
                        boxSizing: 'border-box',
                        backgroundColor: 'grey.800',
                        color: 'white',
                    },
                }}
            >
                <Box sx={{ p: 2, borderBottom: 1, borderColor: 'grey.700' }}>
                    <Typography variant="h6" noWrap component="div">
                        {server?.name}
                    </Typography>
                </Box>
                <List>
                    {server?.channels?.map((channel) => (
                        <ListItem key={channel.id} disablePadding>
                            <ListItemButton
                                selected={selectedChannel?.id === channel.id}
                                onClick={() => setSelectedChannel(channel)}
                            >
                                <ListItemIcon sx={{ color: 'grey.400' }}>
                                    {channel.type === ChannelType.TEXT ? <TagIcon /> : <VolumeUpIcon />}
                                </ListItemIcon>
                                <ListItemText primary={channel.name} />
                            </ListItemButton>
                        </ListItem>
                    ))}
                    <ListItem disablePadding>
                        <ListItemButton>
                            <ListItemIcon sx={{ color: 'grey.400' }}>
                                <AddIcon />
                            </ListItemIcon>
                            <ListItemText primary="Add Channel" />
                        </ListItemButton>
                    </ListItem>
                </List>
            </Drawer> */}
            <Box component="main" sx={{ flexGrow: 1 }}>
                {selectedChannel && (
                    <ChatArea  
                        id={selectedChannel?.id?.toString()||"1"}
                        name={selectedChannel.name}
                    isDM={false}
                />
                )}
            </Box>
        </>
    );
}