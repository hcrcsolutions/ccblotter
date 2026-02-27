import { useRef, useState, useCallback } from 'react';

export function useAudioPlayback() {
  const [playingStepId, setPlayingStepId] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const stepIdRef = useRef<string | null>(null);

  const clearState = useCallback(() => {
    stepIdRef.current = null;
    setPlayingStepId(null);
  }, []);

  const stop = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.removeAttribute('src');
      audioRef.current.load();
    }
    clearState();
  }, [clearState]);

  const play = useCallback(
    (stepId: string, url: string) => {
      // If same step is already playing, stop it
      if (stepIdRef.current === stepId) {
        stop();
        return;
      }

      // Stop any current playback
      if (audioRef.current) {
        audioRef.current.pause();
      } else {
        audioRef.current = new Audio();
        audioRef.current.addEventListener('ended', clearState);
        audioRef.current.addEventListener('error', clearState);
      }

      stepIdRef.current = stepId;
      setPlayingStepId(stepId);
      audioRef.current.src = url;
      audioRef.current.play().catch(clearState);
    },
    [stop, clearState],
  );

  return { playingStepId, play, stop };
}
