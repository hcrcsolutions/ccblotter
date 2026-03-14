'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import { getBackendUrl, getIvrServerUrl } from '../../lib/settings';
import { getNodeColor, getNodeLabel } from './nodeDefaults';
import type { IvrFlowSummary, IvrFlowNode, IvrFlowEdge, IvrFlowVariable, IvrNodeType } from '../../types';

interface FlowTesterDialogProps {
  flow: IvrFlowSummary | null;
  onClose: () => void;
}

interface TestStep {
  nodeType: IvrNodeType;
  nodeLabel: string;
  message: string;
  needsInput: boolean;
  terminal: boolean;
  userInput?: string;
}

interface FlowData {
  nodes: IvrFlowNode[];
  edges: IvrFlowEdge[];
  variables: IvrFlowVariable[];
  entryNodeId: string;
  maxSteps: number;
}

type SessionStatus = 'initializing' | 'running' | 'waiting_input' | 'completed' | 'error';

// Nodes that need user input
const INPUT_NODES: Set<IvrNodeType> = new Set(['COLLECT_DTMF', 'MENU', 'ASR_COLLECT']);
// Terminal nodes that end the flow
const TERMINAL_NODES: Set<IvrNodeType> = new Set(['TRANSFER', 'HANGUP']);

function resolveVariables(template: string, variables: Record<string, unknown>): string {
  return template.replace(/\$\{(\w+)}/g, (_, name) => {
    const val = variables[name];
    return val !== undefined && val !== null ? String(val) : `\${${name}}`;
  });
}

function evaluateCondition(
  config: Record<string, unknown>,
  variables: Record<string, unknown>,
): boolean {
  const varName = config.variableName as string;
  const operator = config.operator as string;
  const compareValue = config.value as string;
  const actual = variables[varName];
  const actualStr = actual !== undefined && actual !== null ? String(actual) : '';

  switch (operator) {
    case 'EQ':
      return actualStr === compareValue;
    case 'NEQ':
      return actualStr !== compareValue;
    case 'GT':
      return Number(actualStr) > Number(compareValue);
    case 'LT':
      return Number(actualStr) < Number(compareValue);
    case 'GTE':
      return Number(actualStr) >= Number(compareValue);
    case 'LTE':
      return Number(actualStr) <= Number(compareValue);
    case 'CONTAINS':
      return actualStr.includes(compareValue);
    case 'MATCHES':
      try {
        return new RegExp(compareValue).test(actualStr);
      } catch {
        return false;
      }
    default:
      return false;
  }
}

function resolveNextNodeId(
  currentNode: IvrFlowNode,
  edges: IvrFlowEdge[],
  variables: Record<string, unknown>,
  userInput?: string,
): string | null {
  const outEdges = edges.filter(e => e.sourceNodeId === currentNode.id);

  // Input nodes: match user input against edge conditions
  if (userInput !== undefined) {
    const match = outEdges.find(e => e.condition === userInput);
    if (match) {
      return match.targetNodeId;
    }
  }

  // CONDITION nodes: evaluate and follow "true"/"false" edge
  if (currentNode.type === 'CONDITION') {
    const result = evaluateCondition(currentNode.config, variables);
    const match = outEdges.find(e => e.condition === String(result));
    if (match) {
      return match.targetNodeId;
    }
  }

  // Default edge (null/"default" condition, or single outgoing edge)
  const defaultEdge = outEdges.find(e => !e.condition || e.condition === 'default');
  if (defaultEdge) {
    return defaultEdge.targetNodeId;
  }
  if (outEdges.length === 1) {
    return outEdges[0].targetNodeId;
  }

  return null;
}

function buildNodeMessage(node: IvrFlowNode, variables: Record<string, unknown>): string {
  const config = node.config;
  switch (node.type) {
    case 'PLAY_PROMPT': {
      const tts = config.ttsText as string;
      const audioUrl = config.audioUrl as string;
      if (tts) {
        return resolveVariables(tts, variables);
      }
      if (audioUrl) {
        return `[Audio: ${audioUrl}]`;
      }
      return '(empty prompt)';
    }
    case 'SET_VARIABLE':
      return `Set ${config.variableName} = ${resolveVariables(String(config.expression ?? ''), variables)}`;
    case 'CONDITION':
      return `${config.variableName} ${config.operator} ${config.value}`;
    case 'WAIT':
      return `Wait ${config.durationSeconds ?? 1}s`;
    case 'SUB_FLOW':
      return `Sub-flow: ${config.subFlowId} (skipped)`;
    case 'NLU_INTENT':
      return `NLU: ${config.inputVariable} (skipped)`;
    case 'COLLECT_DTMF': {
      const prompt = config.prompt as string;
      return prompt ? resolveVariables(prompt, variables) : 'Enter digits';
    }
    case 'MENU': {
      const prompt = config.prompt as string;
      const options = config.validOptions as string[] | undefined;
      let msg = prompt ? resolveVariables(prompt, variables) : 'Select option';
      if (options && options.length > 0) {
        msg += `\nOptions: ${options.join(', ')}`;
      }
      return msg;
    }
    case 'ASR_COLLECT': {
      const prompt = config.prompt as string;
      return prompt ? resolveVariables(prompt, variables) : 'Speak now';
    }
    case 'TRANSFER': {
      const dest = config.destination as string;
      return `Transfer to ${dest || '(unknown)'}`;
    }
    case 'HANGUP': {
      const goodbye = config.goodbyePrompt as string;
      const reason = config.reason as string;
      return goodbye || reason || 'Call ended';
    }
    case 'HTTP_REQUEST':
      return `${config.method || 'GET'} ${config.url || '(no url)'}`;
    default:
      return node.type;
  }
}

export function FlowTesterDialog({ flow, onClose }: FlowTesterDialogProps) {
  const [steps, setSteps] = React.useState<TestStep[]>([]);
  const [status, setStatus] = React.useState<SessionStatus>('initializing');
  const [error, setError] = React.useState<string | null>(null);
  const [inputValue, setInputValue] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const scrollRef = React.useRef<HTMLDivElement>(null);

  // Refs for mutable state used in execution loop
  const flowDataRef = React.useRef<FlowData | null>(null);
  const variablesRef = React.useRef<Record<string, unknown>>({});
  const stepCountRef = React.useRef(0);
  const callIdRef = React.useRef('');
  const currentNodeRef = React.useRef<IvrFlowNode | null>(null);

  // Auto-scroll on new steps
  React.useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [steps]);

  // Initialize session when flow is set
  React.useEffect(() => {
    if (!flow) {
      return;
    }

    setSteps([]);
    setStatus('initializing');
    setError(null);
    setInputValue('');
    stepCountRef.current = 0;
    callIdRef.current = `test-${Date.now()}`;
    currentNodeRef.current = null;
    flowDataRef.current = null;
    variablesRef.current = {};

    initSession(flow);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flow]);

  async function initSession(flowSummary: IvrFlowSummary) {
    try {
      setLoading(true);

      // Fetch flow detail and content in parallel
      const [detailRes, contentRes] = await Promise.all([
        fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowSummary.id}`),
        fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowSummary.id}/content`),
      ]);

      if (!detailRes.ok) {
        throw new Error('Failed to load flow details');
      }
      if (!contentRes.ok) {
        throw new Error('Failed to load flow content');
      }

      const detail = await detailRes.json();
      const contentData = await contentRes.json();

      if (contentData.version === 0 || !contentData.content) {
        throw new Error('Flow has no saved content. Add nodes in the flow editor and save first.');
      }

      const parsed = typeof contentData.content === 'string'
        ? JSON.parse(contentData.content)
        : contentData.content;

      const nodes: IvrFlowNode[] = parsed.nodes || [];
      const edges: IvrFlowEdge[] = parsed.edges || [];
      const variables: IvrFlowVariable[] = parsed.variables || [];

      // Use configured entryNodeId, or auto-detect as the node with no incoming edges
      let entryNodeId = detail.entryNodeId as string | undefined;
      if (!entryNodeId) {
        const targetIds = new Set(edges.map(e => e.targetNodeId));
        const roots = nodes.filter(n => !targetIds.has(n.id));
        if (roots.length === 0) {
          throw new Error('Could not determine entry node: all nodes have incoming edges.');
        }
        entryNodeId = roots[0].id;
      }

      const flowData: FlowData = {
        nodes,
        edges,
        variables,
        entryNodeId,
        maxSteps: detail.maxSteps || 100,
      };
      flowDataRef.current = flowData;

      // Initialize variables from defaults
      const vars: Record<string, unknown> = {};
      for (const v of variables) {
        if (v.defaultValue !== null && v.defaultValue !== undefined) {
          vars[v.name] = v.defaultValue;
        }
      }
      variablesRef.current = vars;

      // Build FlowDefinition and push to ivr-server cache
      const flowDefinition = {
        id: flowSummary.id,
        name: flowSummary.name,
        description: flowSummary.description,
        version: contentData.version,
        status: 'DRAFT',
        entryNodeId,
        maxSteps: detail.maxSteps || 100,
        maxSessionDurationSeconds: detail.maxSessionDurationSeconds || 600,
        nodes: nodes.map(n => ({
          id: n.id,
          type: n.type,
          label: n.label,
          config: n.config,
        })),
        edges,
        variables,
      };

      const ivrUrl = getIvrServerUrl();
      const cacheRes = await fetch(`${ivrUrl}/api/v1/flows`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(flowDefinition),
      });

      if (!cacheRes.ok) {
        const errText = await cacheRes.text().catch(() => '');
        throw new Error(`Failed to push flow to ivr-server: ${cacheRes.status} ${errText}`);
      }

      setLoading(false);

      // Start execution at entry node
      const entryNode = nodes.find(n => n.id === entryNodeId);
      if (!entryNode) {
        throw new Error(`Entry node "${entryNodeId}" not found in flow content`);
      }

      executeFromNode(entryNode);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to initialize test session');
      setStatus('error');
      setLoading(false);
    }
  }

  async function executeFromNode(startNode: IvrFlowNode) {
    const flowData = flowDataRef.current;
    if (!flowData) {
      return;
    }

    let currentNode: IvrFlowNode | null = startNode;
    setStatus('running');

    while (currentNode) {
      stepCountRef.current++;
      if (stepCountRef.current > flowData.maxSteps) {
        addStep({
          nodeType: currentNode.type,
          nodeLabel: currentNode.label,
          message: `Max steps (${flowData.maxSteps}) exceeded — stopping`,
          needsInput: false,
          terminal: true,
        });
        setStatus('completed');
        return;
      }

      // Handle terminal nodes
      if (TERMINAL_NODES.has(currentNode.type)) {
        addStep({
          nodeType: currentNode.type,
          nodeLabel: currentNode.label,
          message: buildNodeMessage(currentNode, variablesRef.current),
          needsInput: false,
          terminal: true,
        });
        setStatus('completed');
        return;
      }

      // Handle input-requiring nodes
      if (INPUT_NODES.has(currentNode.type)) {
        addStep({
          nodeType: currentNode.type,
          nodeLabel: currentNode.label,
          message: buildNodeMessage(currentNode, variablesRef.current),
          needsInput: true,
          terminal: false,
        });
        currentNodeRef.current = currentNode;
        setStatus('waiting_input');
        return;
      }

      // Handle HTTP_REQUEST — delegate to ivr-server
      if (currentNode.type === 'HTTP_REQUEST') {
        addStep({
          nodeType: currentNode.type,
          nodeLabel: currentNode.label,
          message: buildNodeMessage(currentNode, variablesRef.current),
          needsInput: false,
          terminal: false,
        });

        try {
          const ivrUrl = getIvrServerUrl();
          const execRes = await fetch(`${ivrUrl}/api/v1/execute`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              flowId: flow!.id,
              nodeId: currentNode.id,
              callId: callIdRef.current,
              variables: variablesRef.current,
              stepCount: stepCountRef.current,
            }),
          });

          if (!execRes.ok) {
            const errText = await execRes.text().catch(() => '');
            throw new Error(`ivr-server execute failed: ${execRes.status} ${errText}`);
          }

          const execResult = await execRes.json();

          // Merge updated variables
          if (execResult.updatedVariables) {
            variablesRef.current = { ...variablesRef.current, ...execResult.updatedVariables };
          }

          // Update last step message with result
          if (execResult.result) {
            const statusCode = execResult.result.statusCode;
            const bodyPreview = typeof execResult.result.body === 'string'
              ? execResult.result.body.substring(0, 200)
              : JSON.stringify(execResult.result.body ?? '').substring(0, 200);
            updateLastStepMessage(
              `${currentNode.config.method || 'GET'} ${currentNode.config.url} → ${statusCode}\n${bodyPreview}`,
            );
          }
        } catch (e) {
          addStep({
            nodeType: 'HANGUP',
            nodeLabel: 'Error',
            message: e instanceof Error ? e.message : 'HTTP request execution failed',
            needsInput: false,
            terminal: true,
          });
          setStatus('error');
          return;
        }

        // Resolve next node
        const nextId = resolveNextNodeId(currentNode, flowData.edges, variablesRef.current);
        currentNode = nextId ? flowData.nodes.find(n => n.id === nextId) ?? null : null;
        if (!currentNode && nextId) {
          addStep({
            nodeType: 'HANGUP',
            nodeLabel: 'Error',
            message: `Node "${nextId}" not found`,
            needsInput: false,
            terminal: true,
          });
          setStatus('error');
          return;
        }
        continue;
      }

      // Handle locally-simulated nodes
      const message = buildNodeMessage(currentNode, variablesRef.current);

      // SET_VARIABLE: apply the variable
      if (currentNode.type === 'SET_VARIABLE') {
        const varName = currentNode.config.variableName as string;
        const expression = String(currentNode.config.expression ?? '');
        const resolved = resolveVariables(expression, variablesRef.current);
        variablesRef.current = { ...variablesRef.current, [varName]: resolved };
      }

      // CONDITION: add result to message
      let conditionMessage = message;
      if (currentNode.type === 'CONDITION') {
        const result = evaluateCondition(currentNode.config, variablesRef.current);
        conditionMessage = `${message} → ${result}`;
      }

      addStep({
        nodeType: currentNode.type,
        nodeLabel: currentNode.label,
        message: currentNode.type === 'CONDITION' ? conditionMessage : message,
        needsInput: false,
        terminal: false,
      });

      // Resolve next node
      const nextId = resolveNextNodeId(currentNode, flowData.edges, variablesRef.current);
      if (!nextId) {
        addStep({
          nodeType: 'HANGUP',
          nodeLabel: 'End',
          message: 'No outgoing edge — session complete',
          needsInput: false,
          terminal: true,
        });
        setStatus('completed');
        return;
      }

      currentNode = flowData.nodes.find(n => n.id === nextId) ?? null;
      if (!currentNode) {
        addStep({
          nodeType: 'HANGUP',
          nodeLabel: 'Error',
          message: `Node "${nextId}" not found`,
          needsInput: false,
          terminal: true,
        });
        setStatus('error');
        return;
      }
    }
  }

  function addStep(step: TestStep) {
    setSteps(prev => [...prev, step]);
  }

  function updateLastStepMessage(message: string) {
    setSteps(prev => {
      if (prev.length === 0) {
        return prev;
      }
      const updated = [...prev];
      updated[updated.length - 1] = { ...updated[updated.length - 1], message };
      return updated;
    });
  }

  function handleSendInput() {
    if (!inputValue.trim() || status !== 'waiting_input') {
      return;
    }

    const currentNode = currentNodeRef.current;
    const flowData = flowDataRef.current;
    if (!currentNode || !flowData) {
      return;
    }

    const input = inputValue.trim();
    setInputValue('');

    // Annotate the last step with user input
    setSteps(prev => {
      const updated = [...prev];
      const lastIdx = updated.length - 1;
      if (lastIdx >= 0) {
        updated[lastIdx] = { ...updated[lastIdx], userInput: input, needsInput: false };
      }
      return updated;
    });

    // Set result variable
    const resultVar = currentNode.config.resultVariable as string | undefined;
    if (resultVar) {
      variablesRef.current = { ...variablesRef.current, [resultVar]: input };
    }

    // Resolve next node using user input for edge matching
    const nextId = resolveNextNodeId(currentNode, flowData.edges, variablesRef.current, input);
    if (!nextId) {
      addStep({
        nodeType: 'HANGUP',
        nodeLabel: 'End',
        message: 'No matching edge for input — session complete',
        needsInput: false,
        terminal: true,
      });
      setStatus('completed');
      return;
    }

    const nextNode = flowData.nodes.find(n => n.id === nextId);
    if (!nextNode) {
      addStep({
        nodeType: 'HANGUP',
        nodeLabel: 'Error',
        message: `Node "${nextId}" not found`,
        needsInput: false,
        terminal: true,
      });
      setStatus('error');
      return;
    }

    executeFromNode(nextNode);
  }

  function handleClose() {
    // Cleanup: delete flow from ivr-server cache
    if (flow) {
      const ivrUrl = getIvrServerUrl();
      fetch(`${ivrUrl}/api/v1/flows/${flow.id}`, { method: 'DELETE' }).catch(() => {
        // Ignore cleanup errors
      });
    }
    onClose();
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendInput();
    }
  }

  const isOpen = flow !== null;
  const canSendInput = status === 'waiting_input' && inputValue.trim().length > 0;

  return (
    <Dialog
      open={isOpen}
      onClose={handleClose}
      maxWidth="sm"
      fullWidth
      PaperProps={{ sx: { height: '80vh', display: 'flex', flexDirection: 'column' } }}
    >
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1.5 }}>
        <Typography variant="h6" component="span" noWrap sx={{ mr: 2 }}>
          Test: {flow?.name}
        </Typography>
        <IconButton size="small" onClick={handleClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent
        ref={scrollRef}
        sx={{ flex: 1, overflow: 'auto', px: 2, py: 1 }}
      >
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {loading && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 2 }}>
            <CircularProgress size={20} />
            <Typography variant="body2" color="text.secondary">
              Initializing test session...
            </Typography>
          </Box>
        )}

        {steps.map((step, i) => (
          <Paper
            key={i}
            variant="outlined"
            sx={{ p: 1.5, mb: 1, borderLeft: `3px solid ${getNodeColor(step.nodeType)}` }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
              <Chip
                label={getNodeLabel(step.nodeType)}
                size="small"
                sx={{
                  bgcolor: getNodeColor(step.nodeType),
                  color: '#fff',
                  fontWeight: 500,
                  fontSize: '0.7rem',
                  height: 20,
                }}
              />
              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                {step.nodeLabel}
              </Typography>
            </Box>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ whiteSpace: 'pre-wrap', ml: 0.5 }}
            >
              {step.message}
            </Typography>
            {step.userInput !== undefined && (
              <Typography
                variant="body2"
                sx={{ mt: 0.5, ml: 0.5, fontStyle: 'italic', color: 'primary.main' }}
              >
                &rarr; {step.userInput}
              </Typography>
            )}
          </Paper>
        ))}

        {status === 'completed' && steps.length > 0 && (
          <Alert severity="info" sx={{ mt: 1 }}>
            Session complete
          </Alert>
        )}
      </DialogContent>

      {status === 'waiting_input' && (
        <Box sx={{ px: 2, pb: 1, pt: 0.5, display: 'flex', gap: 1 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Enter input..."
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            autoFocus
          />
          <Button
            variant="contained"
            size="small"
            onClick={handleSendInput}
            disabled={!canSendInput}
            sx={{ minWidth: 'auto', px: 2 }}
          >
            <SendIcon fontSize="small" />
          </Button>
        </Box>
      )}

      <DialogActions sx={{ px: 2, py: 1 }}>
        <Button onClick={handleClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
