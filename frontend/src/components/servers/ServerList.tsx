import { useEffect, useState } from 'react';
import { Box, IconButton, List, ListItem, Tooltip, Divider } from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { useNavigate, useParams } from 'react-router-dom';
import serverService from '../../services/server.service';
import { Server } from '../../types/server';

export default function ServerList() {
    const [servers, setServers] = useState<Server[]>([]);
    const navigate = useNavigate();
    const { serverId } = useParams();

    useEffect(() => {
        loadServers();
    }, []);

    const loadServers = async () => {
        try {
            const data = await serverService.getUserServers();
            setServers(data);
        } catch (error) {
            console.error('Failed to load servers:', error);
        }
    };

    return (
        <Box sx={{
            width: 72,
            backgroundColor: 'grey.900',
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            pt: 2,
        }}>
            <List>
                {servers.map((server) => (
                    <ListItem key={server.id} sx={{ mb: 1, p: 0 }}>
                        <Tooltip title={server.name} placement="right">
                            <IconButton
                                onClick={() => navigate(`/servers/${server.id}`)}
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: '50%',
                                    backgroundColor: server.id === Number(serverId) ? 'primary.main' : 'grey.700',
                                    '&:hover': {
                                        backgroundColor: 'primary.dark',
                                    },
                                }}
                            >
                                {server.name.substring(0, 2).toUpperCase()}
                            </IconButton>
                        </Tooltip>
                    </ListItem>
                ))}
                <Divider sx={{ my: 1, backgroundColor: 'grey.700' }} />
                <ListItem sx={{ p: 0 }}>
                    <Tooltip title="Add a Server" placement="right">
                        <IconButton
                            onClick={() => navigate('/servers/new')}
                            sx={{
                                width: 48,
                                height: 48,
                                borderRadius: '50%',
                                backgroundColor: 'grey.700',
                                '&:hover': {
                                    backgroundColor: 'success.main',
                                },
                            }}
                        >
                            <AddIcon />
                        </IconButton>
                    </Tooltip>
                </ListItem>
            </List>
        </Box>
    );
}