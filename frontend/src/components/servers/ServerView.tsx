import { useEffect, useState } from 'react';
import {
  Box, Typography, Avatar, IconButton,
  Drawer, useMediaQuery, useTheme,
} from '@mui/material';
import TagIcon from '@mui/icons-material/Tag';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import AddIcon from '@mui/icons-material/Add';
import MenuOpenIcon from '@mui/icons-material/MenuOpen';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import { useNavigate, useParams } from 'react-router-dom';
import { Server, Channel, ChannelType } from '../../types/server';
import ChatArea from '../chat/ChatArea';
import CreateChannelModel from '../servers/CreateChannelModel';
import { useChannels } from '../../hooks/useChannels';
import { useStatus } from '../../hooks/useStatus';
import { UserStatus } from '../../types/status';
import StatusIndicator from '../user/StatusIndicator';
import { useServers } from '../../hooks/useServers';

export default function ServerView() {
  const { serverId, channelId } = useParams();
  const theme = useTheme();
  const { servers } = useServers();
  const navigate = useNavigate();
  // Breakpoints
  // channel sidebar: inline on md+ (≥900px), Drawer on xs/sm
  const hideSidebar  = useMediaQuery(theme.breakpoints.down('md'));
  // members panel: inline on xl+ (≥1536px), Drawer on lg, hidden on md and below
  const hideMembers  = useMediaQuery(theme.breakpoints.down('xl'));
  const showMembers  = useMediaQuery(theme.breakpoints.up('md')); // don't even show toggle below md

  const [server, setServer]               = useState<Server | null>(null);
  const [selectedChannel, setSelectedChannel] = useState<Channel | null>(null);
  const [createOpen, setCreateOpen]       = useState(false);
  const [sidebarOpen, setSidebarOpen]     = useState(false);  // mobile channel drawer
  const [membersOpen, setMembersOpen]     = useState(false);  // members drawer

  const serverIdNum = serverId ? parseInt(serverId) : 0;
  const channelIdNum = channelId ? parseInt(channelId) : 0;
  const { channels, createChannel ,isLoading: channelsLoading 
  } = useChannels(serverIdNum);
  const { friendStatuses } = useStatus();
  const getStatus = (id: number) => friendStatuses[id] || UserStatus.OFFLINE;

  const handleChannelClick = (channel: Channel) => () => {
    navigate(`/channels/${serverId}/${channel.id}`);
    setSelectedChannel(channel);
  };
  
  useEffect(() => {
    if(servers.isLoading || servers.data.length==0 ||servers.isError) return;
    const srv = servers.data?.find(s => s.id === serverIdNum) || null;
    setServer(srv);
    
  },[serverIdNum,channelIdNum,servers.data,servers.isLoading,servers.isError]);



  useEffect(() => {
    if (channelsLoading ) return;
    const ch = channels?.find(c => c.id === channelIdNum) || null;
    if(ch==null && channels!=null && channels.length>0) 
   { setSelectedChannel(channels[0]);navigate(`/channels/${serverId}/${channels[0].id}`);}
    else
    setSelectedChannel(ch);

  }, [channelIdNum, channels,channelsLoading]);




  // Auto-close channel drawer when a channel is selected on mobile
  useEffect(() => {
    if (hideSidebar) setSidebarOpen(false);
  }, [selectedChannel]);

 

  // ── Part 2: Channel Sidebar ──────────────────────────────────────────────
  const ChannelSidebar = (
    <Box sx={{
      width: { xs: 280, md: 240 },
      flexShrink: 0,
      bgcolor: '#2b2d31',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      borderRight: '1px solid #1e1f22',
    }}>
      {/* Server name header */}
      <Box sx={{
        height: 48,
        flexShrink: 0,
        px: 2,
        display: 'flex',
        alignItems: 'center',
        fontWeight: 700,
        fontSize: 15,
        color: '#f2f3f5',
        borderBottom: '1px solid #1e1f22',
        cursor: 'pointer',
        '&:hover': { bgcolor: '#35373c' },
      }}>
        {server?.name ?? 'Channels'}
      </Box>

      {/* Channel list */}
      <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', py: 1 }}>
        {channels?.map((channel) => {
          const isSelected = selectedChannel?.id === channel.id;
          return (
            <Box
              key={channel.id}
              onClick={handleChannelClick(channel)}
              sx={{
                display: 'flex',
                alignItems: 'center',
                px: 2, py: 0.75, mx: 1,
                borderRadius: 1,
                cursor: 'pointer',
                color: isSelected ? '#f2f3f5' : '#949ba4',
                bgcolor: isSelected ? '#404249' : 'transparent',
                transition: 'background .1s, color .1s',
                '&:hover': { bgcolor: '#35373c', color: '#f2f3f5' },
              }}
            >
              <Box sx={{ mr: 1.5, color: '#80848e', display: 'flex' }}>
                {channel.type === ChannelType.TEXT
                  ? <TagIcon sx={{ fontSize: 18 }} />
                  : <VolumeUpIcon sx={{ fontSize: 18 }} />}
              </Box>
              <Box sx={{
                fontSize: 14,
                fontWeight: isSelected ? 500 : 400,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}>
                {channel.name}
              </Box>
            </Box>
          );
        })}

        {/* Add Channel */}
        <Box
          onClick={() => setCreateOpen(true)}
          sx={{
            display: 'flex', alignItems: 'center',
            px: 2, py: 0.75, mx: 1, mt: 0.5,
            borderRadius: 1, cursor: 'pointer',
            color: '#949ba4',
            '&:hover': { bgcolor: '#35373c', color: '#f2f3f5' },
          }}
        >
          <AddIcon sx={{ fontSize: 18, mr: 1.5 }} />
          <Box sx={{ fontSize: 14 }}>Add Channel</Box>
        </Box>
      </Box>
    </Box>
  );

  // ── Part 4: Members Panel ────────────────────────────────────────────────
  // In a real app pass actual server members here; using empty array as placeholder
  const serverMembers: any[] = [];

  const MembersPanel = (
    <Box sx={{
      width: { xs: 280, xl: 240 },
      flexShrink: 0,
      bgcolor: '#2b2d31',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      borderLeft: '1px solid #1e1f22',
    }}>
      {/* Header */}
      <Box sx={{
        height: 48,
        flexShrink: 0,
        px: 2,
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        fontSize: 11,
        fontWeight: 700,
        color: '#949ba4',
        letterSpacing: '.05em',
        textTransform: 'uppercase',
        borderBottom: '1px solid #1e1f22',
      }}>
        Members
      </Box>

      {/* Members list */}
      <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', p: 1.5 }}>
        {serverMembers.length === 0 ? (
          <Typography sx={{ color: '#5c5f66', fontSize: 13, px: 1, pt: 1 }}>
            No members to show
          </Typography>
        ) : (
          <>
            {/* Online */}
            {serverMembers.filter(m => getStatus(m.id) === UserStatus.ONLINE).length > 0 && (
              <>
                <Typography sx={sectionLabel}>
                  ONLINE — {serverMembers.filter(m => getStatus(m.id) === UserStatus.ONLINE).length}
                </Typography>
                {serverMembers
                  .filter(m => getStatus(m.id) === UserStatus.ONLINE)
                  .map(m => <MemberRow key={m.id} member={m} status={UserStatus.ONLINE} />)}
              </>
            )}
            {/* Offline */}
            {serverMembers.filter(m => getStatus(m.id) !== UserStatus.ONLINE).length > 0 && (
              <>
                <Typography sx={{ ...sectionLabel, mt: 2 }}>
                  OFFLINE — {serverMembers.filter(m => getStatus(m.id) !== UserStatus.ONLINE).length}
                </Typography>
                {serverMembers
                  .filter(m => getStatus(m.id) !== UserStatus.ONLINE)
                  .map(m => <MemberRow key={m.id} member={m} status={UserStatus.OFFLINE} />)}
              </>
            )}
          </>
        )}
      </Box>
    </Box>
  );

  return (
    // Fragment fills MainLayout's Outlet flex slot
    <Box sx={{ flex: 1, minWidth: 0, display: 'flex', overflow: 'hidden' }}>

      {/* ── Part 2: Channel sidebar — inline md+, Drawer on xs/sm ── */}
      {hideSidebar ? (
        <Drawer
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          PaperProps={{ sx: { bgcolor: '#2b2d31', border: 'none' } }}
        >
          {ChannelSidebar}
        </Drawer>
      ) : (
        ChannelSidebar
      )}

      {/* ── Part 3: Chat area ── */}
      <Box sx={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        bgcolor: '#313338',
        overflow: 'hidden',
      }}>
        {/* Mobile top bar */}
        {(hideSidebar || (hideMembers && showMembers)) && (
          <Box sx={{
            height: 48,
            flexShrink: 0,
            bgcolor: '#313338',
            borderBottom: '1px solid #1e1f22',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 1.5,
          }}>
            {/* Left: open channel sidebar (mobile only) */}
            {hideSidebar && (
              <IconButton
                size="small"
                onClick={() => setSidebarOpen(true)}
                sx={{ color: '#b5bac1' }}
              >
                <MenuOpenIcon fontSize="small" />
              </IconButton>
            )}

            {/* Channel name */}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flex: 1, px: 1 }}>
              <TagIcon sx={{ fontSize: 18, color: '#80848e' }} />
              <Typography sx={{ fontSize: 14, fontWeight: 600, color: '#f2f3f5' }}>
                {server?.name ? `Server: ${server.name}` : 'Select a channel'}
              </Typography>
            </Box>

            {/* Right: open members panel (md–xl) */}
            {hideMembers && showMembers && (
              <IconButton
                size="small"
                onClick={() => setMembersOpen(true)}
                sx={{ color: '#b5bac1' }}
              >
                <PeopleAltIcon fontSize="small" />
              </IconButton>
            )}
          </Box>
        )}

        {/* Chat content */}
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          {channels.length > 0 && selectedChannel != null && selectedChannel.id != null ? (
            <ChatArea
              id={selectedChannel.id.toString()}
              name={selectedChannel.name}
              isDM={false}
            />
          ) : (
            <Box sx={{
              flex: 1, display: 'flex', alignItems: 'center',
              justifyContent: 'center', color: '#5c5f66',
            }}>
              <Typography sx={{ fontSize: 14 }}>
                { server==null ? `Server Don't exist` :( channels.length === 0 ? 'No channels yet — add one!' : 'Select a channel')}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>

      {/* ── Part 4: Members panel — inline xl+, Drawer on md–xl, hidden below md ── */}
      {hideMembers ? (
        // Drawer for md / lg screens (toggle button in top bar)
        <Drawer
          open={membersOpen}
          onClose={() => setMembersOpen(false)}
          anchor="right"
          PaperProps={{ sx: { bgcolor: '#2b2d31', border: 'none' } }}
        >
          {MembersPanel}
        </Drawer>
      ) : (
        // Inline on xl+
        MembersPanel
      )}

      {/* Create channel modal */}
      <CreateChannelModel
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreate={(data) => {
          createChannel({ serverId: serverIdNum, channel: data });
          setCreateOpen(false);
        }}
      />
    </Box>
  );
}

/* ── Sub-components ── */
const MemberRow = ({ member, status }: { member: any; status: UserStatus }) => (
  <Box sx={{
    display: 'flex', alignItems: 'center', gap: 1.5,
    px: 1, py: 0.75, borderRadius: 1, cursor: 'pointer',
    '&:hover': { bgcolor: '#35373c' },
  }}>
    <Box sx={{ position: 'relative', flexShrink: 0 }}>
      <Avatar
        src={member.avatarUrl}
        sx={{ width: 32, height: 32, bgcolor: '#5865f2', fontSize: 13 }}
      >
        {member.username?.[0]?.toUpperCase()}
      </Avatar>
      <StatusIndicator status={status} borderColor="#2b2d31" />
    </Box>
    <Typography sx={{
      fontSize: 14,
      color: status === UserStatus.ONLINE ? '#f2f3f5' : '#5c5f66',
      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
    }}>
      {member.username}
    </Typography>
  </Box>
);

/* ── Styles ── */
const sectionLabel = {
  color: '#949ba4',
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: '.05em',
  textTransform: 'uppercase' as const,
  px: 1,
  mb: 0.5,
};