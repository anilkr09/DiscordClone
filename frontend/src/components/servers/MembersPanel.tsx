import { Box, Typography, Avatar } from '@mui/material';
import StatusIndicator from '../user/StatusIndicator';
import { useStatus } from '../../hooks/useStatus';
import { UserStatus } from '../../types/status';

{"// Pass in your server's members — adapt type to your own Member type"}
export default function MembersPanel({ members = [] }: { members?: any[] }) {
  const { friendStatuses } = useStatus();
  const getStatus = (id: number) => friendStatuses[id] || UserStatus.OFFLINE;

  const online  = members.filter(m => getStatus(m.id) === UserStatus.ONLINE);
  const offline = members.filter(m => getStatus(m.id) !== UserStatus.ONLINE);

  return (
    <Box sx={{ p: 1.5 }}>

      {online.length > 0 && (
        <>
          <Typography sx={sectionLabel}>ONLINE — {online.length}</Typography>
          {online.map(m => <MemberRow key={m.id} member={m} status={UserStatus.ONLINE} />)}
        </>
      )}

      {offline.length > 0 && (
        <>
          <Typography sx={{ ...sectionLabel, mt: 2 }}>OFFLINE — {offline.length}</Typography>
          {offline.map(m => <MemberRow key={m.id} member={m} status={UserStatus.OFFLINE} />)}
        </>
      )}

    </Box>
  );
}

const MemberRow = ({ member, status }: { member: any; status: UserStatus }) => (
  <Box sx={{
    display: 'flex', alignItems: 'center', gap: 1.5,
    px: 1, py: 0.75, borderRadius: 1, cursor: 'pointer',
    '&:hover': { bgcolor: '#35373c' },
  }}>
    <Box sx={{ position: 'relative', flexShrink: 0 }}>
      <Avatar src={member.avatarUrl} sx={{ width: 32, height: 32, bgcolor: '#5865f2', fontSize: 13 }}>
        {member.username?.[0]?.toUpperCase()}
      </Avatar>
      <StatusIndicator status={status} borderColor="#2b2d31" />
    </Box>
    <Typography sx={{
      fontSize: 14, color: status === UserStatus.ONLINE ? '#f2f3f5' : '#5c5f66',
      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
    }}>
      {member.username}
    </Typography>
  </Box>
);

const sectionLabel = {
  color: '#949ba4', fontSize: 11, fontWeight: 700,
  letterSpacing: '.05em', textTransform: 'uppercase',
  px: 1, mb: 0.5,
};
