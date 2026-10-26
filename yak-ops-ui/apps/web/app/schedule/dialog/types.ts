export interface ScheduleDialogProps {
  targetId: string;
  published: boolean;
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
  onViewInstance: (id: string) => void;
  historyScheduleId?: string;
}
