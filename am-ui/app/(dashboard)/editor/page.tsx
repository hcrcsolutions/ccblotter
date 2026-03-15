'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import DatacenterEditor from '../../components/Editor/DatacenterEditor';
import TrunkEditor from '../../components/Editor/TrunkEditor';
import SbcEditor from '../../components/Editor/SbcEditor';
import EdgeEditor from '../../components/Editor/EdgeEditor';
import BusinessUnitEditor from '../../components/Editor/BusinessUnitEditor';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel({ children, value, index }: TabPanelProps) {
  return (
    <Box
      role="tabpanel"
      hidden={value !== index}
      sx={{ flex: 1, display: value === index ? 'flex' : 'none', flexDirection: 'column', overflow: 'hidden' }}
    >
      {value === index && children}
    </Box>
  );
}

export default function EditorPage() {
  const [tabIndex, setTabIndex] = React.useState(0);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTabIndex(newValue);
  };

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tabs value={tabIndex} onChange={handleTabChange}>
          <Tab label="Datacenters" />
          <Tab label="Trunks" />
          <Tab label="SBCs" />
          <Tab label="Connections" />
          <Tab label="Business Units" />
        </Tabs>
      </Box>

      <TabPanel value={tabIndex} index={0}>
        <DatacenterEditor />
      </TabPanel>
      <TabPanel value={tabIndex} index={1}>
        <TrunkEditor />
      </TabPanel>
      <TabPanel value={tabIndex} index={2}>
        <SbcEditor />
      </TabPanel>
      <TabPanel value={tabIndex} index={3}>
        <EdgeEditor />
      </TabPanel>
      <TabPanel value={tabIndex} index={4}>
        <BusinessUnitEditor />
      </TabPanel>
    </Box>
  );
}
