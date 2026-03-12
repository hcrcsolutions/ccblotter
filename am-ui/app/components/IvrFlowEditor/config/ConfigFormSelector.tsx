'use client';

import * as React from 'react';
import Typography from '@mui/material/Typography';
import type { IvrNodeType } from '../../../types';
import { PlayPromptConfigForm } from './PlayPromptConfigForm';
import { CollectDtmfConfigForm } from './CollectDtmfConfigForm';
import { MenuConfigForm } from './MenuConfigForm';
import { TransferConfigForm } from './TransferConfigForm';
import { HangupConfigForm } from './HangupConfigForm';
import { SetVariableConfigForm } from './SetVariableConfigForm';
import { ConditionConfigForm } from './ConditionConfigForm';
import { SubFlowConfigForm } from './SubFlowConfigForm';

import { WaitConfigForm } from './WaitConfigForm';
import { HttpRequestConfigForm } from './HttpRequestConfigForm';
import { AsrCollectConfigForm } from './AsrCollectConfigForm';
import { NluIntentConfigForm } from './NluIntentConfigForm';

interface ConfigFormSelectorProps {
  nodeType: IvrNodeType;
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function ConfigFormSelector({ nodeType, config, onChange }: ConfigFormSelectorProps) {
  switch (nodeType) {
    case 'PLAY_PROMPT': return <PlayPromptConfigForm config={config} onChange={onChange} />;
    case 'COLLECT_DTMF': return <CollectDtmfConfigForm config={config} onChange={onChange} />;
    case 'MENU': return <MenuConfigForm config={config} onChange={onChange} />;
    case 'TRANSFER': return <TransferConfigForm config={config} onChange={onChange} />;
    case 'HANGUP': return <HangupConfigForm config={config} onChange={onChange} />;
    case 'SET_VARIABLE': return <SetVariableConfigForm config={config} onChange={onChange} />;
    case 'CONDITION': return <ConditionConfigForm config={config} onChange={onChange} />;
    case 'SUB_FLOW': return <SubFlowConfigForm config={config} onChange={onChange} />;

    case 'WAIT': return <WaitConfigForm config={config} onChange={onChange} />;
    case 'HTTP_REQUEST': return <HttpRequestConfigForm config={config} onChange={onChange} />;
    case 'ASR_COLLECT': return <AsrCollectConfigForm config={config} onChange={onChange} />;
    case 'NLU_INTENT': return <NluIntentConfigForm config={config} onChange={onChange} />;
    default:
      return <Typography color="text.secondary">No configuration for this node type.</Typography>;
  }
}
