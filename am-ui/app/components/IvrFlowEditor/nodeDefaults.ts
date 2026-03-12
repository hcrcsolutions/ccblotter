import type { IvrNodeType } from '../../types';

export function getDefaultConfig(type: IvrNodeType): Record<string, unknown> {
  switch (type) {
    case 'PLAY_PROMPT':
      return { ttsText: '', audioUrl: '', bargeIn: false };
    case 'COLLECT_DTMF':
      return { prompt: '', maxDigits: 1, terminatingKey: '#', timeoutSeconds: 5, interDigitTimeoutSeconds: 3, resultVariable: '' };
    case 'MENU':
      return { prompt: '', validOptions: [], timeoutSeconds: 5, maxRetries: 3, invalidPrompt: '', timeoutPrompt: '', resultVariable: '' };
    case 'TRANSFER':
      return { destination: '', transferType: 'AGENT', whisperPrompt: '' };
    case 'HANGUP':
      return { reason: '', goodbyePrompt: '' };
    case 'SET_VARIABLE':
      return { variableName: '', expression: '' };
    case 'CONDITION':
      return { variableName: '', operator: 'EQ', value: '' };
    case 'SUB_FLOW':
      return { subFlowId: '', inputMappings: {}, outputMappings: {} };
    case 'WAIT':
      return { durationSeconds: 1, musicOnHoldUrl: '' };
    case 'HTTP_REQUEST':
      return { url: '', method: 'GET', headers: {}, bodyTemplate: '', responseVariable: '', timeoutSeconds: 10 };
    case 'ASR_COLLECT':
      return { prompt: '', language: 'en-US', timeoutSeconds: 5, resultVariable: '', bargeIn: true };
    case 'NLU_INTENT':
      return { inputVariable: '', expectedIntents: [], resultVariable: '', confidenceThreshold: 0.7 };
    default:
      return {};
  }
}

export function getNodeColor(type: IvrNodeType): string {
  switch (type) {
    case 'PLAY_PROMPT':
      return '#4caf50';
    case 'COLLECT_DTMF':
      return '#2196f3';
    case 'MENU':
      return '#9c27b0';
    case 'TRANSFER':
      return '#ff9800';
    case 'HANGUP':
      return '#f44336';
    case 'SET_VARIABLE':
      return '#607d8b';
    case 'CONDITION':
      return '#795548';
    case 'SUB_FLOW':
      return '#3f51b5';
    case 'WAIT':
      return '#8bc34a';
    case 'HTTP_REQUEST':
      return '#e91e63';
    case 'ASR_COLLECT':
      return '#00bcd4';
    case 'NLU_INTENT':
      return '#673ab7';
    default:
      return '#9e9e9e';
  }
}

export function getNodeLabel(type: IvrNodeType): string {
  switch (type) {
    case 'PLAY_PROMPT': return 'Play Prompt';
    case 'COLLECT_DTMF': return 'Collect DTMF';
    case 'MENU': return 'Menu';
    case 'TRANSFER': return 'Transfer';
    case 'HANGUP': return 'Hangup';
    case 'SET_VARIABLE': return 'Set Variable';
    case 'CONDITION': return 'Condition';
    case 'SUB_FLOW': return 'Sub Flow';

    case 'WAIT': return 'Wait';
    case 'HTTP_REQUEST': return 'HTTP Request';
    case 'ASR_COLLECT': return 'ASR Collect';
    case 'NLU_INTENT': return 'NLU Intent';
    default: return type;
  }
}

interface NodeGroup {
  label: string;
  types: IvrNodeType[];
}

export const NODE_GROUPS: NodeGroup[] = [
  {
    label: 'Standard',
    types: ['PLAY_PROMPT', 'COLLECT_DTMF', 'MENU', 'TRANSFER', 'HANGUP', 'SET_VARIABLE', 'CONDITION', 'SUB_FLOW', 'WAIT'],
  },
  {
    label: 'HTTP',
    types: ['HTTP_REQUEST'],
  },
  {
    label: 'AI',
    types: ['ASR_COLLECT', 'NLU_INTENT'],
  },
];
