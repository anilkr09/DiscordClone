import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import ServerList from '../servers/ServerList';

export default function MainLayout() {
    return (
        <Box sx={{ display: 'flex', height: '100vh' }}>
            <ServerList />
            <Box sx={{ flexGrow: 1, display: 'flex' }}>
                <Outlet />
            </Box>
        </Box>
    );
}